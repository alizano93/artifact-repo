#!/usr/bin/env ruby
require 'net/ssh'

require_relative '../welog'
require_relative '../transfer'
require_relative '../installer'
require_relative '../runnerinstaller'
require_relative '../deploy-ssh'

private_key=File.read("test_key")

test_cmd = RunnerInstaller.new("ls -al")
deploy_remote = DeploySsh.new('localhost',
                  options = {user: 'root', pass: true,
                  key_data: [private_key],
                  passphrase: "testy"})
deploy_remote.install(test_cmd)
