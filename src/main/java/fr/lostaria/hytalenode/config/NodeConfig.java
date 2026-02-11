package fr.lostaria.hytalenode.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class NodeConfig {

    private boolean enable;
    private String managerUrl;
    private String authUrl;
    private String pubsubUrl;
    private String deviceToken;
    private String currentHostIp;
    private int portRangeStart;
    private int portRangeEnd;

}
