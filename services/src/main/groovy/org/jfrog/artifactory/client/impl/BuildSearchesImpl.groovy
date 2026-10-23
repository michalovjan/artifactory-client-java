package org.jfrog.artifactory.client.impl

import org.apache.http.entity.ContentType
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.BuildSearches
import org.jfrog.artifactory.client.PromotionFilters
import org.jfrog.artifactory.client.aql.AqlQueryBuilder
import org.jfrog.artifactory.client.model.AqlBuild
import org.jfrog.artifactory.client.model.AqlBuildSearchResults

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static org.jfrog.artifactory.client.aql.AqlItem.aqlItem

/**
 * Fluent builder for AQL searches in the 'builds' domain.
 * <p>
 * Every call modifies the underlying {@link AqlQueryBuilder} immediately. Filters that target the same
 * AQL field (e.g. project/repo, name/namePrefix) therefore override each other - the last call wins.
 */
class BuildSearchesImpl implements BuildSearches {

    static final String BUILD_INFO_REPO_SUFFIX = "-build-info"
    private static final String[] DEFAULT_INCLUDE =
            ["name", "number", "created", "created_by", "modified", "modified_by", "started", "repo", "promotion"]

    private final ArtifactoryImpl artifactory
    private final String searchApi
    private final AqlQueryBuilder queryBuilder = AqlQueryBuilder.builds().include(DEFAULT_INCLUDE)
    private final PromotionFilters promotionFilters

    BuildSearchesImpl(Artifactory artifactory, String searchApi) {
        this.artifactory = artifactory as ArtifactoryImpl
        this.searchApi = searchApi
        this.promotionFilters = new PromotionFiltersImpl(queryBuilder, this)
    }

    @Override
    BuildSearches project(String project) {
        repo(mandatory(project, "project") + BUILD_INFO_REPO_SUFFIX)
    }

    @Override
    BuildSearches repo(String repo) {
        queryBuilder.item(aqlItem("repo", mandatory(repo, "repo")))
        this
    }

    @Override
    BuildSearches name(String name) {
        queryBuilder.item(aqlItem("name", mandatory(name, "name")))
        this
    }

    @Override
    BuildSearches namePrefix(String namePrefix) {
        mandatory(namePrefix, "namePrefix")
        queryBuilder.match("name", namePrefix.endsWith("*") ? namePrefix : namePrefix + "*")
        this
    }

    @Override
    BuildSearches number(String number) {
        queryBuilder.item(aqlItem("number", mandatory(number, "number")))
        this
    }

    @Override
    BuildSearches createdSince(ZonedDateTime since) {
        queryBuilder.gte("created", toAqlTimestamp(since))
        this
    }

    @Override
    PromotionFilters promotion() {
        promotionFilters
    }

    @Override
    BuildSearches include(String... fields) {
        queryBuilder.include(fields)
        this
    }

    @Override
    BuildSearches limit(int limit) {
        queryBuilder.limit(limit)
        this
    }

    @Override
    BuildSearches offset(int offset) {
        queryBuilder.offset(offset)
        this
    }

    @Override
    List<AqlBuild> doSearch() {
        return byAql(queryBuilder.build())
    }

    /**
     * Formats the given zoned timestamp as ISO-8601 with its offset, which AQL accepts
     * (e.g. 2024-01-15T10:00+01:00[Europe/Prague] becomes "2024-01-15T10:00:00+01:00").
     *
     * @throws IllegalArgumentException if the timestamp is null
     */
    static String toAqlTimestamp(ZonedDateTime timestamp) {
        mandatory(timestamp, "since").format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * Null check for request arguments.
     *
     * @throws IllegalArgumentException with message "&lt;name&gt; is mandatory" if the value is null
     */
    static <T> T mandatory(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is mandatory")
        }
        value
    }

    @Override
    List<AqlBuild> byAql(String aqlQuery) {
        mandatory(aqlQuery, "aqlQuery")
        String url = "${searchApi}aql"
        AqlBuildSearchResults searchResults = artifactory.post(url, ContentType.TEXT_PLAIN, aqlQuery, null, AqlBuildSearchResults, null)
        return searchResults?.getResults() ?: new ArrayList<AqlBuild>()
    }
}
