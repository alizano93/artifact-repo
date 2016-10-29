package com.digitalglobe.hydrate.security.module.manager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.digitalglobe.hydrate.logging.factory.HydrateLoggerFactory;
import com.digitalglobe.hydrate.logging.logger.HydrateLogger;
import com.safenetinc.luna.LunaSlotManager;

@Service
public class LunaManager {

    private final static HydrateLogger LOGGER = HydrateLoggerFactory.getDefaultHydrateLogger(LunaManager.class);

    @Value("${luna.login.password}")
    private String password;

    @Autowired
    private LunaSlotManager slotManager;

    public void login() {

	try {
	    slotManager.login(password);
	} catch (Exception e) {
	    LOGGER.error(e, "Exception during login:");
	}
    }

    public void logout() {

	
	slotManager.logout();
    }
}