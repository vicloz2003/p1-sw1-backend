
package com.ibpms.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlFlow {
    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private String guardCondition;
}

