class Transfer
  attr_reader :source, :destination, :direction, :sudo

  def initialize(source, destination, direction = :local, options)
    Welog.log.debug "Direction: #{direction.to_s}"
    @source = source
    @destination = destination
    if direction != :local
#      if direction.to_s != 'download' or direction.to_s != 'upload'
#        raise "Please specify :upload or :download as symbol"
#      end
    end
    @direction = direction
    @sudo = true if options[:sudo]
  end
end
