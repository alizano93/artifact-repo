class RunnerInstaller < Installer
    def initialize(commands, options={})
      super()
      @sudo_cmd = 'sudo'
      @cmds = [commands].flatten
      @sudo = true if options[:sudo]

      begin
        raise "This requires at least one command" if @cmds.nil?
      rescue
        Welog.log.error """
          Fsailed to build runner object
          Provisioner will try to continue running other commands
          """
        @cmds = ['echo "empty command"']
      end
    end

    protected

    def install_commands
        cmds = @sudo ? @cmds.map { |cmd| "#{@sudo_cmd} #{cmd}"} : @cmds
        cmds
    end
end
