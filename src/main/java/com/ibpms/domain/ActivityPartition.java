
package com.ibpms.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPartition {
    private String id;
    private String label;
    private String departmentId;
}

