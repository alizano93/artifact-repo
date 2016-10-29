#!/usr/bin/env ruby
#/ Usage: <hsmclient-setup> [-b] bucket-name
#/                          [-k] object-name that holds priv key
#/                          [-p] object-name that holds passphrase on 1st line
#/                          [-u] system user who will own key pair
#/                          [-d] destination path for key pair installation
#/                          [-i] HSM ip address
#/ Fetches SSH private key and passphrase from s3 using instance role
#/ credentials and installs them locally for later use by cloudhsm cli commands.
#/ Configures Linux HSM client using aws cli cloudhsm commands.
#/ Example:
#/ ./hsmclient-setup.rb -b cloudhsm-ssh-keypair \
#/                      -k hsm-client-key \
#/                      -u luisurrea \
#/                      -p passphrase.yml \
#/                      -d /Users/luisurrea/dev/docker/docker-mise-en-place \
#/                      -i 10.0.201.209
#/                      -a arn:aws:cloudhsm:us-east-1:111222137685:hapg-f243e0d7
$stderr.sync = true

require 'optparse'
require 'aws-sdk'
require 'tempfile'
require 'logger'
require_relative 'welog'
require_relative 'keypairinstaller'
require_relative 'fileinstaller'
require_relative 'filescp'
require_relative 'runnerinstaller'
require_relative 's3secrets'
require_relative 'hsmclient'
require_relative 'deploy-local'
require_relative 'deploy-ssh'

# default options
flag    = false
region = 'us-east-1'
bucket_name  = 'cloudhsm-ssh-keypair'
passphrase_filename = 'passphrase.yml'
key     = ''
owner = 'ec2-user'
mode = '400'
destination_path = '/home/ec2-user'
keys_folder='.ssh'
ip_address = ''
manager_host = 'hsm-manager'
prefix = 'hydrate-mise'
safenet_dir = '/usr/safenet/lunaclient/cert'
hapg = 'arn:aws:cloudhsm:us-east-1:111222137685:hapg-f243e0d7'

# parse arguments
file = __FILE__
ARGV.options do |opts|
  opts.on("-f", "--flag")              { flag = true }
  opts.on("-b", "--opt=val", String)   { |val| bucket_name = val }
  opts.on("-p", "--opt=val", String)   { |val| passfile = val}
  opts.on("-k", "--opt=val", String)   { |val| key = val}
  opts.on("-u", "--opt=val", String)   { |val| owner = val}
  opts.on("-d", "--opt=val", String)   { |val| destination_path = val}
  opts.on("-i", "--opt=val", String)   { |val| ip_address = val}
  opts.on("-a", "--opt=val", String)   { |val| hapg = val}
  opts.on_tail("-h", "--help")         { exec "grep ^#/<'#{file}'|cut -c4-" }
  opts.parse!
end

#Initial resources setup
Welog.log.info("Initializing resource objects".colorize("yellow"))

hostname = Socket.gethostbyname(Socket.gethostname).first
client_name = prefix + '-' + hostname
client_id = client_name

sshcfg_contents = <<EOC
Host #{ip_address}
    Hostname #{ip_address}
    User manager
    IdentityFile #{destination_path}/#{keys_folder}/#{key}
EOC

chrystoki_contents = <<EOC
Chrystoki2 = {
   LibUNIX = /usr/lib/libCryptoki2.so;
   LibUNIX64 = /usr/lib/libCryptoki2_64.so;
}
Luna = {
  DefaultTimeOut = 500000;
  PEDTimeout1 = 100000;
   PEDTimeout2 = 200000;
  PEDTimeout3 = 10000;
  KeypairGenTimeOut = 2700000;
  CloningCommandTimeOut = 300000;
  CommandTimeOutPedSet = 720000;
}
CardReader = {
  RemoteCommand = 1;
}
Misc = {
  PE1746Enabled = 0;
   ToolsDir = /usr/safenet/lunaclient/bin;
}
LunaSA Client = {
   ReceiveTimeout = 20000;
   SSLConfigFile = /usr/safenet/lunaclient/bin/openssl.cnf;
   ClientPrivKeyFile = /usr/safenet/lunaclient/cert/client/#{client_name}Key.pem;
   ClientCertFile = /usr/safenet/lunaclient/cert/client/#{client_name}.pem;
   ServerCAFile = /tmp/CAFile.pem;
   NetClient = 1;
   ServerName00 = #{ip_address};
   ServerPort00 = 1792;
   ServerHtl00 = 0;
EOC


destination = destination_path + '/' + keys_folder
s3secrets = S3Secrets.new(region, bucket_name, key, passphrase_filename)

keypair = KeyPairInstaller.new(destination, key, s3secrets,
            options = {owner: owner, mode: mode, pass: true})

keypair.pre(["test ! -d #{destination_path}/#{keys_folder} " \
              "&& mkdir -p #{destination_path}/#{keys_folder}; echo done"])

sshconfig = FileInstaller.new(destination, 'config', sshcfg_contents,
                  options = {owner: owner, mode:'644', sudo: true})

chrystoki_config = FileInstaller.new('/etc/', 'Chrystoki.conf', chrystoki_contents,
                  options = {owner: owner, mode:'644', sudo: true})

deploy_local = DeployLocal.new()
deploy_local.install(keypair)
deploy_local.install(sshconfig)
deploy_local.install(chrystoki_config)

#Establish Network Trust Link
Welog.log.info "Establishing Network Trust Link NTLS with HSM Appliance".colorize("yellow")
vtlcreatecert = RunnerInstaller.new(["vtl createCert -n #{client_name}"])
deploy_local.install(vtlcreatecert)

deploy_hsmserver = DeploySsh.new(ip_address, options = {user: 'manager', 
                                   keys: ['/home/hsmclient/.ssh/hsm-client-key'], 
                                   passphrase: s3secrets.passphrase})

Welog.log.info "Downloading server certficate from HSM Appliance".colorize("yellow")
server_pem = FileScp.new("server.pem", destination_path, :download)
deploy_hsmserver.install(server_pem)
Welog.log.info "Uploading client certficate to HSM Appliance".colorize("yellow")
client_pem = FileScp.new(safenet_dir + '/client/' + client_name + ".pem", ".", :upload)                                 
deploy_hsmserver.install(client_pem)

Welog.log.info "Proceeding to start NTLS link".colorize("yellow")
client_register= RunnerInstaller.new(["client register -client #{client_id} -hostname #{client_name}"])
deploy_hsmserver.install(client_register)
#vtladdserver = RunnerInstaller.new(["vtl addServer -n #{ip_address} -c #{destination}/server.pem"], sudo: true)
#deploy_local.install(vtladdserver)

certificate = File.read(safenet_dir + '/client/' + client_name + ".pem")
hsmclient = HsmClient.new(region, client_name, certificate)


#TODO: check that provided hapg exists

hsmclient_loadagent = RunnerInstaller.new("./load-agent.exp #{destination_path}/.ssh/#{key} #{s3secrets.passphrase}")
deploy_local.install(hsmclient_loadagent)

#Register Client
Welog.log.info "Registering Client with HSM HAPG: #{hapg}".colorize("yellow")
hsmclient_register_hapg = RunnerInstaller.new("cloudhsm register-client-to-hapg --aws-region #{region} --client-arn #{hsmclient.client_arn} --hapg-arn #{hapg}")
deploy_local.install(hsmclient_register_hapg)

cert_dir = safenet_dir + '/server/'
config_dir = '/etc/'
Welog.log.info "Obtaining Server certificate and configuration for Registered client".colorize("yellow")
hsmclient_get_config = RunnerInstaller.new("cloudhsm get-client-configuration --aws-region #{region} --client-arn #{hsmclient.client_arn} --hapg-arn #{hapg} --cert-directory #{cert_dir} --config-directory #{config_dir}")
deploy_local.install(hsmclient_get_config)
Welog.log.info "Checking Partitions joined".colorize("yellow")
hsmclient_verify = RunnerInstaller.new("vtl verify")
deploy_local.install(hsmclient_verify)
Welog.log.info "Verifying Client Configuration".colorize("yellow")
hsmclient_admin = RunnerInstaller.new("vtl haAdmin show")
deploy_local.install(hsmclient_admin)
#Get HSM Client Configuration
#client_config_contents, client_cert_contents = hsmclient.get_config(client_arn: hsmclient.client_arn,
#                                                                  client_version: "5.1",
#                                                                  hapg_list: [hapg])

#Install config and credentials
#client_config = FileInstaller.new(destination, client_name + '_config',
#                                  client_config_contents,
#                                  options = {owner: owner, mode: 644, sudo: true})
#client_cert = FileInstaller.new(destination, client_name + '_cert',
#                               client_cert_contents,
#                               options = {owner: owner, mode: 644, sudo: true})

#deploy_local.install(client_config)
#deploy_local.install(client_cert)

warn "ARGV:     #{ARGV.inspect}"
warn "flag:     #{flag.inspect}"
warn "option:   #{owner.inspect}"
warn "option:   #{bucket_name.inspect}"
warn "option:   #{key.inspect}"
warn "option:   #{destination_path.inspect}"
warn "option:   #{ip_address.inspect}"
