package com.wagepayroll.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.billing.usage-aggregation", name = "scheduled-enabled", havingValue = "true",
		matchIfMissing = true)
public class BillingUsageAggregationSchedulingConfiguration {
}
