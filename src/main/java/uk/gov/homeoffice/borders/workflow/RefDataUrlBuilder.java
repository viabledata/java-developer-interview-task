package uk.gov.homeoffice.borders.workflow;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.homeoffice.borders.workflow.config.RefDataBean;
import uk.gov.homeoffice.borders.workflow.identity.TeamQuery;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.*;

import static java.util.Optional.ofNullable;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class RefDataUrlBuilder {
    private static final String LOCATION = "/v2/entities/location";
    private static final String TEAM = "/v2/entities/team";

    private RefDataBean refDataBean;

    public String getLocation(String currentLocationId) {
        return UriComponentsBuilder
                .newInstance()
                .uri(refDataBean.getUrl())
                .path(LOCATION)
                .query("filter=id=eq.{currentLocationId}")
                .query("mode=dataOnly")
                .buildAndExpand(Collections.singletonMap("currentLocationId", currentLocationId))
                .toString();
    }

    public String teamByCode(String teamId) {
        return UriComponentsBuilder.newInstance()
                .uri(refDataBean.getUrl())
                .path(TEAM)
                .query("filter=code=eq.{teamId}")
                .query("mode=dataOnly")
                .buildAndExpand(Collections.singletonMap("teamId", teamId))
                .toString();
    }

    public String teamByCodeIds(String... teamIds) {
        return UriComponentsBuilder.newInstance()
                .uri(refDataBean.getUrl())
                .path(TEAM)
                .query("filter=code=in.({teamIds})")
                .query("mode=dataOnly")
                .buildAndExpand(Collections.singletonMap("teamIds", teamIds))
                .toString();
    }

    public String teamQuery(TeamQuery team) {
        Map<String, Object> variables = new HashMap<>();
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .uri(refDataBean.getUrl())
                .path(TEAM);

        ofNullable(team.getName()).ifPresent(name -> {
            variables.put("name", name);
            builder.query("filter=name=eq.{name}&mode=dataOnly");
        });

        ofNullable(team.getId()).ifPresent(id -> {
            variables.put("id", id);
            builder.query("filter=id=eq.{id}");
        });

        ofNullable(team.getNameLike()).ifPresent(nameLike -> {
            variables.put("nameLike", nameLike);
            builder.query("filter=name=like.{nameLike}");
        });
        ofNullable(team.getIds()).ifPresent(ids -> {
            List<String> idsForProcessing = Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.toList());
            String idsToProcess = StringUtils.join(idsForProcessing, ",");
            variables.put("ids", idsToProcess);
            builder.query("filter=id=in.({ids})");
        });

        return builder
                .buildAndExpand(variables)
                .toString();
    }

    public String teamById(String teamId) {
        return UriComponentsBuilder.newInstance()
                .uri(refDataBean.getUrl())
                .path(TEAM)
                .query("filter=id=eq.{teamId}")
                .query("mode=dataOnly")
                .buildAndExpand(Collections.singletonMap("teamId", teamId))
                .toString();
    }

    public String teamChildrenByParentTeamId(String parentTeamId) {
        return UriComponentsBuilder.newInstance()
                .uri(refDataBean.getUrl())
                .path(TEAM)
                .query("filter=parentteamid=eq.{parentTeamId}")
                .query("mode=dataOnly")
                .buildAndExpand(Collections.singletonMap("parentTeamId", parentTeamId))
                .toString();
    }
}
