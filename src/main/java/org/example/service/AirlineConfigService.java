package org.example.service;

import org.example.model.AirlineOrg;
import org.example.model.PayloadAttributes;

public interface AirlineConfigService {

    AirlineOrg getAirlineConfiguration(String airlineId, PayloadAttributes payloadAttributes);
}