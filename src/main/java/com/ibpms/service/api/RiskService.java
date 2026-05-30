package com.ibpms.service.api;

import com.ibpms.dto.response.PolicyRiskResponse;

/**
 * Intelligent risk engine (RF-3). Builds health features for ACTIVE process instances from
 * MongoDB and delegates the scoring to the ibpms_ml autoencoder (unsupervised Deep Learning).
 */
public interface RiskService {

    /** Assess all ACTIVE instances of a policy: delay risk, anomalies and resource priority. */
    PolicyRiskResponse assessPolicy(String policyId);
}
