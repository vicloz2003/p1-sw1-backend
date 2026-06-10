package com.ibpms.service.api;

import com.ibpms.dto.response.RouteAdvisoryResponse;

/**
 * Gateway to the DL route predictor (ibpms_ml, RF-3.1). Computes the decision-time context of
 * an instance from MongoDB, asks the model which outgoing branch historically led to the best
 * outcome, and returns an <b>advisory</b> recommendation.
 *
 * <p>This whole capability is optional and isolated: it is gated by the
 * {@code ibpms.routing.advisor.enabled} flag and never touches the workflow engine, so it can
 * be disabled or removed without affecting process execution.
 */
public interface RouteAdvisorService {

    /**
     * @param instanceId the process instance to advise
     * @param nodeId     the decision node to evaluate; {@code null} ⇒ the instance's current node
     */
    RouteAdvisoryResponse adviseForInstance(String instanceId, String nodeId);

    /** Proxies the DL models' evaluation metrics (accuracy, AUC, detection rate) for transparency. */
    Object modelMetrics();
}
