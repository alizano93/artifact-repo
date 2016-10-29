require 'net/ssh'

class DeploySsh
  def initialize(host, options={})
    require 'net/scp'
    @host = host
    @user = options[:user] ? options[:user] : 'root'
    if options[:pass]
      options.delete(:pass)
      options.delete(:user)
      @options = options
      @options[:keys] = []
    else
      options.delete(:user)
      @options = options
    end 
  end

  def install(installer)
    @installer = installer
    process(installer.install_sequence)
  end

  def process(*commands)
    prepare_commands(commands[0]).each do |cmd|
      if cmd.is_a?(Transfer) && cmd.direction == :upload
        res = upload(cmd.source, cmd.destination)
      elsif cmd.is_a?(Transfer) && cmd.direction == :download
        res = download(cmd.source, cmd.destination)
      elsif cmd.is_a?(Transfer) && cmd.direction == :local
        Welog.log.info "Please deploy local if you intend to tranfer locally"
      else
        res = run_command(cmd)
      end
    end
    return true
  end

  def prepare_commands(commands)
    return commands unless @sudo
    commands.map do |cmd|
      cmd.match(/^sudo/) ? cmd : "sudo #{command}"
    end
  end

  def upload(source, destination, &block)
    begin
      Net::SCP.start(@host, @user, @options) do |scp|
        scp.upload!(source, destination) do |ch, name, sent, total|
          block.call(ch, name, sent, total) if block
        end
      end
    rescue Exception => error
      Welog.log.error error
      raise error
    end
  end

  def download(source, destination, &block)
    begin
      key_manager = Net::SSH::Authentication::KeyManager.new(nil, @options)
      Net::SCP.start(@host, @user, @options) do |scp|
        scp.download!(source, destination) do |ch, name, sent, total|
          block.call(ch, name, sent, total) if block
        end
      end
    rescue Exception => error
      raise error
    end
  end

  def run_command(cmd)
    session = ssh_start()
    Welog.log.info "Running command: #{cmd} on remote host #{@host}"
    if channel_runner(session, cmd) != 0
      Welog.log.error "Failed to execute #{cmd} on #{@host}"
      raise "Failed to execute #{cmd} on #{@host}"
    else
      Welog.log.debug "Successfullly ran command: #{cmd} on #{@host}"
    end
  end

  def ssh_start()
    if @options.length < 1
      Net::SSH.start(@host, @user)
    else
      Net::SSH.start(@host, @user, @options)
    end
  end

  def channel_runner(session_handle, cmd)
    session_handle.open_channel do |chan|
      chan.on_data do |chan, data|
        Welog.log.info "Host: #{@host} replied with -->\n#{data}\n"
      end
      chan.on_extended_data do |chan, int, data|
        next unless int == 1
        Welog.log.error "Error received: #{data}"
      end
      chan.on_request("exit-status") do |chan, data|
        @exit_status = data.read_long
        if @exit_status == 0
          Welog.log.info "Success!"
        else
          Welog.log.error "Error executing command #{cmd} on remote host #{@host}"
        end
      end
      chan.on_request("exit-signal") do |chan, data|
        We.log.info "#{cmd} was signaled with: #{data.read_long}"
      end
      chan.exec cmd do |chan, status|
        Welog.log.error "Error executing #{cmd}" unless status
      end
    end
    session_handle.loop
    Welog.log.debug "Exit status #{@exit_status}"
    @exit_status
  end
end
