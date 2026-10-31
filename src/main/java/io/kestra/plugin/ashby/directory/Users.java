package io.kestra.plugin.ashby.directory;

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
@Schema(title = "List Ashby directory")
@Plugin
public class Users extends AbstractAshbyList {
    @Override
    protected String endpoint() { return "user.list"; }
}
