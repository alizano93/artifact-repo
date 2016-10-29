class S3Secrets
  #TODO: make passphrase optional
  def initialize(region, bucket_name, key, passphrase_filename)
    client = Aws::S3::Client.new(region: region)
    s3 = Aws::S3::Resource.new(client: client)
    bucket = s3.bucket(bucket_name)
    @key = key
    @passphrase_filename = passphrase_filename
    if bucket.exists?
      @bucket = bucket
    else
      Welog.log.fatal """
        S3 Bucket: #{bucket_name} does not exist.
        Terminating provisioner. Giving up fetching credentials
      """
      exit 1
    end
  end

  def private_key
    @private_key ||= fetch_private_key
  end

  def passphrase
    @passphrase ||= fetch_passphrase
  end

  private

  def fetch_private_key
    Welog.log.info "Fetching private key file from s3 file: #{@key}"
    privkey_obj = @bucket.object(@key)
    privkey_contents = Tempfile.new(@key)
    privkey_obj.get(response_target: privkey_contents)
    privkey_contents
  end

  def fetch_passphrase
    Welog.log.info "Fetching passphrase file from s3 file: #{@passphrase_filename}"
    passfile_obj = @bucket.object(@passphrase_filename)
    passobj = passfile_obj.get()
    read_passphrase(passobj.body)
  end

  def read_passphrase(passphrase_obj)
    pass = YAML.load(passphrase_obj)
    if pass == false
      begin
        raise "Unable to obtain passphrase from credentials"
      rescue
        Welog.log.fatal """
          Passphrase defined as required and not able to obtain passphrase
          """
        exit 1
      end
    else
      pass['passphrase'] if pass['passphrase']
    end
  end
end
