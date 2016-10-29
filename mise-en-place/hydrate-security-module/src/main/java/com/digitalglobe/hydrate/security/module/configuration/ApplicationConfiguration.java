package com.digitalglobe.hydrate.security.module.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.digitalglobe.hydrate.security.module.manager.LunaManager;
import com.digitalglobe.hydrate.security.module.manager.KeyManager;
import com.digitalglobe.hydrate.security.module.cli.ApplicationCLI; 
import com.digitalglobe.hydrate.security.module.store.SecureStore; 
import com.safenetinc.luna.LunaSlotManager;

@Configuration
public class ApplicationConfiguration   {
 
    @Value("${hydrate.master.key.name:hydrateDefaultMasterKey}")
    private String masterKeyName;
    
    @Value("${hydrate.s3.bucket.name:hydrate-s3-bucket-name}")
    private String s3BucketName;
    
    @Bean
    public SecureStore secureStore() {
	//""hydrate-itar-data-bucket";
	System.out.println(String.format("masterKey [%s] and bucketName [%s]", masterKeyName, s3BucketName));
	return new SecureStore(masterKeyName, s3BucketName);
	//return new SecureStore("hydrate-itar-data-bucket","hydrateDefaultMasterKey");
    }
    
    @Bean
    public KeyManager keyManager() {
	return new KeyManager();
    }
    
    @Bean
    public LunaManager lunaManager() {
	return new LunaManager();
    }
    
    @Bean
    public LunaSlotManager lunaSlotManager() {
	return LunaSlotManager.getInstance();
    }
    
    @Bean
    public ApplicationCLI applicationCLI() {
        return new ApplicationCLI();
    }    
    
}
