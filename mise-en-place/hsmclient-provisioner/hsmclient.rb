class HsmClient
  attr_reader :label, :client_arn, :hapg_list

  def initialize(region, label, certificate)
    @client = Aws::CloudHSM::Client.new(region: region)
    res = @client.create_luna_client(label: label, certificate: certificate)
    if res.successful?
      @label = label
      @client_arn = res.client_arn
    else
      Welog.log.fatal "Error in API call creating HSM client"
      raise "Error in API call creating HSM client. Terminating!"
    end
    list_hapgs
  end

  def list_hapgs
    res = @client.list_hapgs
    if res.successful?
      @hapg_list = res.hapg_list
      Welog.log.info "Found list of HA PGs for account with size #{@hapg_list.length}"
    else
      Welog.log.fatal "Cannot determine list of HA PG's at the moment"
      raise "Error in obtaining HA PG to register client"
    end
  end

  def get_config(options)
    res = @client.get_config(options)
    if res.successful?
      return res.config_file, res.config_cred
    else
      Welog.log.fatal "Cannot obtain client configuration from HSM service"
      raise "Error obtaining client configuration details from HSM"
    end
  end
end
