class FileInstaller < Installer
    def initialize(destination_path, filename, contents, options = {})
      pn = Pathname.new(destination_path)
      dir, base = pn.split
      @destination = destination_path
      @basename = filename
      @contents = contents

      raise "need :contents key for file" unless @contents

      super()

      owner options[:owner] if options[:owner]
      mode options[:mode] if options[:mode]
      @sudo = true if options[:sudo]
      setup_source
    end

    def install_commands
      destination = @destination + "/" + @basename
      if @sudo
        [Transfer.new(@sourcepath, destination, :local, sudo: false)]
      else
        [Transfer.new(@sourcepath, destination, :local, sudo: true)]
      end
    end

    def owner(owner)
      @owner = owner
      post ["chown #{owner} #{@destination}/#{@basename}"]
    end

    def mode(mode)
      @mode = mode
      post ["chmod #{mode} #{@destination}/#{@basename}"]
    end

    def setup_source
      raise "No contents obtained for file deployment" unless @contents
      @file = Tempfile.new(@basename)
      @file.print @contents
      @sourcepath = @file.path
      @file.close
    end
end
