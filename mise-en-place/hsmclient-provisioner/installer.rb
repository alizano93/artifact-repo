class Installer
  def initialize
    @pre = []; @post = []
  end

  def pre(*commands)
    @pre += commands
  end

  def post(*commands)
    @post += commands
  end

  def install_sequence
    commands = @pre + install_commands() + @post
    flatten commands
  end

  protected

  def flatten(commands)
    commands.flatten.map {|c| c.is_a?(Proc) ? c.call : c }.flatten
  end

  def install_commands
    raise 'Actual installer needs to override this method to specify what to do'
  end
end
