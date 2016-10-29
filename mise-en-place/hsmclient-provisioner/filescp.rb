class FileScp < Installer
  def initialize(source, destination, direction)
    @sourcepath = source
    @destination = destination
    @direction = direction
    Welog.log.debug "#{@sourcepath}, #{@destination}, #{@direction}"
    super()
  end

  def install_commands
    [Transfer.new(@sourcepath, @destination, @direction, sudo: false)]
  end
end
