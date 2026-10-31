package io.kestra.plugin.ashby.jobs;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.plugin.ashby.AbstractAshbyList;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "List Ashby jobs")
@Plugin
public class List extends AbstractAshbyList {
    @Override
    protected String endpoint() { return "job.list"; }
}
