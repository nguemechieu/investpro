package org.investpro.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Transaction page metadata model.
 *
 * Maps payloads like:
 * {
 *   "count": 4,
 *   "from": "2016-06-22T18:41:52.655959799Z",
 *   "lastTransactionID": "6412",
 *   "pageSize": 100,
 *   "pages": ["..."],
 *   "to": "2016-06-22T18:41:52.660593788Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {

    @JsonProperty("count")
    private int count;

    @JsonProperty("from")
    private Instant from;

    @JsonProperty("lastTransactionID")
    private String lastTransactionID;

    @JsonProperty("pageSize")
    private int pageSize;

    @JsonProperty("pages")
    private List<String> pages;

    @JsonProperty("to")
    private Instant to;
}
