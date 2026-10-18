package com.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MdmApplication {

	public static void main(String[] args) {
		var context=SpringApplication.run(MdmApplication.class, args);
		if(context.getEnvironment().getProperty("mdm.bootstrap.enabled",Boolean.class,false))context.close();
	}

}
