package com.cosmetic.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CosmeticEcommerceBackendApplication {

	public static void main(String[] args) throws java.io.IOException {
		if (com.cosmetic.ecommerce.release.PublicCatalogServer.startIfSelected()) return;
		SpringApplication.run(CosmeticEcommerceBackendApplication.class, args);
	}

}
