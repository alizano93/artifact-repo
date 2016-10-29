require 'pathname'
require 'yaml'
require_relative 'installer'
require_relative 'transfer'

class KeyPairInstaller < Installer

  def initialize(destination_path, filename, s3secrets ,options = {})
    pn = Pathname.new(destination_path)
    dir, base = pn.split
    @destination = destination_path
    @basename = filename
    @contents = s3secrets.private_key
    @pass_required = options[:pass] if options[:pass]
    @passphrase = s3secrets.passphrase if @pass_required

    begin
      raise "need :contents key for file" unless @contents
    rescue
      Welog.log.fatal "Unable to obtain private key for installation"
      exit 1
    end

    super()

    owner options[:owner] if options[:owner]
    mode options[:mode] if options[:mode]
    setup_source_priv
    validate_priv_key(@sourcepath_priv)
    setup_source_pub
  end

  def install_commands
    destination_priv = @destination + '/' + @basename
    destination_pub = @destination + '/' + @basename + '.pub'
    [Transfer.new(@sourcepath_priv, destination_priv, :local, sudo: false),
    Transfer.new(@sourcepath_pub, destination_pub, :local, sudo: false)]
  end

  def owner(owner)
    @owner = owner
    post ["chown #{owner} #{@destination}/#{@basename}"]
  end

  def mode(mode)
    @mode = mode
    post ["chmod #{mode} #{@destination}/#{@basename}"]
  end

  private

  def setup_source_priv
    @sourcepath_priv = @contents.path
    @contents.close
  end

  def validate_priv_key(sourcepath)
    if @pass_required
      ret = system("openssl rsa -in #{sourcepath} -check -passin pass:#{@passphrase} > /dev/null")
    else
      ret = system("openssl rsa -in #{sourcepath} -check  > /dev/null")
    end

    begin
      raise "Not a valid private rsa key #{sourcepath}" unless ret
    rescue
      #TODO: Do we want to retry obtaining private key?
      Welog.log.fatal "Obtained private key does not seem to be a valid rsa private key
        Provisioner won't be able to register client with CloudHSM"
      exit 1
    end
  end

  def setup_source_pub
    if @pass_required
      contents = `ssh-keygen -y -P #{@passphrase} -f #{@sourcepath_priv}`
    else
      contents = `ssh-keygen -y -f #{@sourcepath_priv}`
    end

    begin
      raise "Unable to obtain public key from private" unless contents
    rescue
      #TODO: Try downloading from S3
      Welog.log.fatal "Unable to obtain public key from private key using ssh-keygen"
      exit 1
    end
    @file_pub = Tempfile.new(@basename + '.pub')
    @file_pub.print contents
    @file_pub.close
    @sourcepath_pub = @file_pub.path
  end
end
