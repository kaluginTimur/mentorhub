package com.mentorhub.mail.internal.delivery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailSenderProperties.class)
@Import(JavaMailDelivery.class)
class MailDeliveryConfiguration {}
