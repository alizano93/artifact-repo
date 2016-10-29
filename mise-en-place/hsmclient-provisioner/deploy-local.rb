require 'open4'

class DeployLocal
  def install(installer)
    @installer = installer
    process(installer.install_sequence)
  end

  def process(*commands)
    commands[0].each do |comm|
      if comm.is_a?(Transfer)
        if comm.sudo
            res = transfer(comm.source, comm.destination, true)
        else
            res = transfer(comm.source, comm.destination, false)
        end
      else
        res = run_command(comm)
      end
      Welog.log.error "Failed to locally run command #{comm}" if res != 0
    end
    return true
  end

  def run_command(command)
    Welog.log.info "Running command #{command} locally"
    status = Open4::popen4(command) do |pid, stdin, stdout, stderr|
      stdout.read.split("\n").each do |line|
        Welog.log.info "[#{pid}] stdout: #{line}"
      end
    end
    status.to_i
  end

  def transfer(source, destination, sudo)
    if sudo
      run_command "sudo cp #{source} #{destination}"
    else
      run_command "cp #{source} #{destination}"
    end
  end
end
