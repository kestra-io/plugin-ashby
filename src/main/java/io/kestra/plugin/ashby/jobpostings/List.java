package io.kestra.plugin.ashby.jobpostings;

import java.util.Map;
import io.kestra.plugin.ashby.AbstractAshbyList;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "List Ashby job postings", description = "Retrieves published job postings, including unlisted postings unless listedOnly is true, following all available cursor pages.")
@Plugin(examples = {@Example(title = "List Ashby job postings", full = true, code = """
    id: list_ashby_job_postings
    namespace: company.team
    tasks:
      - id: list
        type: io.kestra.plugin.ashby.jobpostings.List
        apiKey: "{{ secret('ASHBY_API_KEY') }}"
        fetchType: STORE
    """)})
public class List extends AbstractAshbyList {
    @PluginProperty(group = "source")
    @Schema(title = "listedOnly", description = "If true, return only publicly listed postings. Omitted by default.")
    private Property<Boolean> listedOnly;

    @PluginProperty(group = "source")
    @Schema(title = "includeUnpublishedJobPostings", description = "If true, include unpublished draft postings. Omitted by default.")
    private Property<Boolean> includeUnpublishedJobPostings;

    @PluginProperty(group = "source")
    @Schema(title = "location", description = "Filter by location name, case sensitive. Omitted by default.")
    private Property<String> location;

    @PluginProperty(group = "source")
    @Schema(title = "department", description = "Filter by department name, case sensitive. Omitted by default.")
    private Property<String> department;

    @Override
    protected String endpoint() {
        return "jobPosting.list";
    }

    @Override
    protected Map<String, Object> requestFilters(RunContext runContext) throws Exception {
        var body = super.requestFilters(runContext);
        putFilter(runContext, body, "listedOnly", listedOnly, Boolean.class);
        putFilter(runContext, body, "includeUnpublishedJobPostings", includeUnpublishedJobPostings, Boolean.class);
        putFilter(runContext, body, "location", location, String.class);
        putFilter(runContext, body, "department", department, String.class);
        return body;
    }
}
