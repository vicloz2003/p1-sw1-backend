
package com.ibpms.domain;

import com.ibpms.domain.enums.NodeType;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityNode {
    private String id;
    private String label;
    private String partitionId;
    private NodeType type;
    private Map<String, Object> formSchema;
    private Map<String, String> metadata;
}

