---
title: "Dependency Manipulation"
---

* Contents
{:toc}

### Overview

GME can override a set of dependency versions using a remote REST endpoint as its input source. The tool uses the same code from PME and therefore supports a subset of the features from [here](https://release-engineering.github.io/pom-manipulation-ext/guide/dep-manip.html)


#### REST Endpoint

GME can prescan the project, collect up all used `group:artifact:version` and call a REST endpoint using the endpoint property `restURL` (provided from the Dependency Analysis tool [here](https://github.com/project-ncl/dependency-analysis), versions >= 2.1, hereinafter referred to as DA), which will then return a list of possible new versions. Note that the URL should be the subset of the endpoint e.g.

    http://foo.bar.com/da/rest/v-1

GME will then call the following endpoints

    lookup/maven/latest
    lookup/maven

in that order. By default GME will pass *all* the GAVs to the endpoint **automatically sizing** the data sent to DA according to the project size. Note that the initial split batches can also be configured manually via `-DrestMaxSize=<...>`. If that value is set to 0, then everything is sent without any auto-sizing. If the endpoint returns a 503 or 504 timeout the batch is automatically split into smaller chunks in an attempt to reduce load on the endpoint and the request retried. It will by default chunk down to size of 4 before aborting. This can be configured with `-DrestMinSize=<...>`.

A boolean flag `restBrewPullActive` flag switches on and off the version lookup in Brew and the default value is false. Switching it off might have positive effect on performance. Finally, the string identifier `restMode` indicates type of versions to lookup. By default it is empty. Modes are configurable in DA, so it is needed to check the DA config/consult with DA maintainers for the list of configured modes. Usual modes might be e.g.

- `PERSISTENT`
- `TEMPORARY`
- `SERVICE`
- `SERVICE-TEMPORARY`

with more to be added in the future. The lookup REST endpoint should follow:

<table>
<tr>
   <th id="Parameters">Parameters</th>
   <th id="Returns">Returns</th>
</tr>
<tr>
<td>
   <pre style="font-size: 10px"><code class="language-json">
[
    [ "brewPullActive": true, ]
    [ "mode": "MODE-ID", ]
    {
        "groupId": "org.foo",
        "artifactId": "bar",
        "version": "1.0.0.Final"
    },
    ...
]
    </code></pre>
</td>
<td>
   <pre style="font-size: 10px"><code class="language-json">
[
    {
        "groupId": "org.foo",
        "artifactId": "bar",
        "version": "1.0.0.Final",
        "bestMatchVersion": "1.0.0.Final-rebuild-2",
    },
    ...
]
   </code></pre>
</td>
</tr>
</table>

##### REST Timeouts and retries

In case of a 504 response from DA, by default the operations will be retried after a waiting period of 30 seconds. This value can be optionally configured with `-DrestRetryDuration=<...>`, expressed in seconds.

The underlying HTTP client library responsible for calling the REST endpoints is set by default with a socket timeout of 10 minutes, and a connection timeout of 30 seconds. The values can be optionally configured respectively with `-DrestSocketTimeout=<...>` and `-DrestConnectionTimeout=<...>`, expressed in seconds.

##### REST Headers

You can add custom HTTP headers to your REST calls by setting a property named `restHeaders`. HTTP headers must be comma-separated and each non-empty name-value pair must be colon-separated.

For example:

    -DrestHeaders=log-user-id:102,log-request-context:061294ff-088,log-process-context:,log-expires:,log-tmp:

##### REST PNC-Specific properties

As of new versions of DA (version TBD), it makes available 3 additional ways how you can influence returned results. By
default, DA returns highest version with the highest version suffix. With `-DrestDependencyRanks`,
`-DrestDependencyAllowList`, `-DrestDependencyDenyList` you can alter this behaviour if you want to highlight some PNC
specific properties of the dependencies.

The general framework about how to express these PNC-Specific qualities is done through so called Qualifiers. Examples
of such Qualifiers are `PRODUCT`(PNC Product), `VERSION` (PNC Product Version), `MILESTONE` (PNC Product Milestone) or
`QUALITY` (PNC Artifact Quality) and more... These Qualifier have their respective values like `PRODUCT:RHSSO`,
`VERSION:RHSSO 7.2`, `MILESTONE:RHSSO 7.2.3.CR2`. If an PNC-made Artifact was built by Build which was a part of RHSSO
Product. A Qualifier `PRODUCT:RHSSO` would match that artifact or dependency in PME's context.

With `restDependencyRanks` you can express a preference which artifacts you prefer more than others. It consists of
separate ranks where each rank is delimited by `;` (can be modified by `-DrestDependencyRankDelimiter=','`). Simple
example of ranking could be`-DrestDependencyRanks="PRODUCT:RHSSO"`. What would happen is, that DA will look at all
versions of a dependency and sort them by the Product and version suffix at the same time.
With some example versions and `-DrestDependencyRanks="PRODUCT:RHSSO"` best version would be picked like this.
(highlighted version is picked for that dependency)

| Version       | Product | Quality  | SORTED -->><br/><<--PRE-SORT | Version           | Product   | Quality      |
|---------------|---------|----------|------------------------------|-------------------|-----------|--------------|
| release-00006 | EAP     |          |                              | **release-00005** | **RHSSO** | **RELEASED** |
| release-00002 |         |          |                              | release-00004     | RHSSO     |              |
| release-00003 | EAP     | RELEASED |                              | release-00006     | EAP       |              |
| release-00005 | RHSSO   | RELEASED |                              | release-00002     |           |              |
| release-00001 |         |          |                              | release-00003     | EAP       | RELEASED     |
| release-00004 | RHSSO   |          |                              | release-00001     |           |              |

Order of ranks matter, with higher rank comes higher priority. Additionally, ranks 'cooperate' with each other. Versions
with multiple ranks that match will be prioritized.

For example `-DrestDependencyRanks="PRODUCT:EAP;QUALITY:RELEASED"` would result in this order.

| Version       | Product | Quality  | SORTED -->><br/><<--PRE-SORT | Version           | Product | Quality      |
|---------------|---------|----------|------------------------------|-------------------|---------|--------------|
| release-00006 | EAP     |          |                              | **release-00003** | **EAP** | **RELEASED** |
| release-00002 |         |          |                              | release-00006     | EAP     |              |
| release-00003 | EAP     | RELEASED |                              | release-00005     | RHSSO   | RELEASED     |
| release-00005 | RHSSO   | RELEASED |                              | release-00004     | RHSSO   |              |
| release-00001 |         |          |                              | release-00002     |         |              |
| release-00004 | RHSSO   |          |                              | release-00001     |         |              |


`restDependencyAllowList` and `restDependencyDenyList` are fairly straightforward. They are comma-seperated
(non-configurable) list of Qualifiers that will, in case of allowList, be **exclusively** allowed or in case of
denyList, **not allowed at all**.

An example:

    -DrestDependencyAllowList="PRODUCT:EAP, PRODUCT:RHSSO"

    -DrestDependencyDenyList="QUALITY:BLACKLISTED"

Furthermore, you can change the scope of affected dependencies for specific groupIDs or groupID:artifactIDs. For example
`-DrestDependencyRanks.org.slf4j='PRODUCT:EAP'` would apply only to artifacts with `org.slf4j` groupID. This is the case
also for `-DrestDependencyAllowList.org.slf4j:slf4j-api` and `-DrestDependencyAllowList.org.slf4j:slf4j-api`.

Full list of available Qualifiers and much more in-depth guide how ranking system works is available in other document
(TBD).

### Disabling Dependency Manipulation

If the parameter `-DdependencySource=NONE` is set then this will disable communication with the REST source which effectively disables manipulation. Note that the version will still be changed - although, if there is no preexisting `manipulation.json` it will always get a `-00001` suffix.

### Exclusions and Overrides

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Previously <i>dependencyExclusion</i> was also available providing functionality
that was also available via <i>dependencyOverride</i>. As of GME 3.3 this has been removed.
</td>
</tr>
</table>

In a multi-module build it is considered good practice to coordinate dependency version among the modules using dependency management.
In other words, if modules `A` and `B` both use dependency `X`, both modules should use the same version of dependency `X`.
Therefore, the default behaviour of this extension is to use a single set of dependency versions applied to all modules.

It is possible to flexibly override or exclude a dependency globally or on a per module basis. The property starts with `dependencyOverride.` and has the following format:

    gradle generateAlignmentMetadata -DdependencyOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=[version] | ,+[group:artifact]...


**Note:** Multiple exclusions may be added using multiple instances of `-DdependencyOverride...`.

**Note** Wildcards for `artifactId` are supported e.g. `-DdependencyOverride.org.apache.commons:*@org.acme.subproject:*=1.0.0.rebuild-2`

#### Global Version Override

Doing the following

    gradle generateAlignmentMetadata -DdependencyOverride.junit:junit@*=4.10-rebuild-10

will, throughout the entire project (due to the wildcard), apply the explicit `4.10-rebuild-10` version to the `junit:junit` dependency.


#### Per-Module Version Override

However, there are certain cases where it is useful to use different versions of the same dependency in different modules. For example, if the project includes integration code for multiple versions of a particular API. In that case it is possible to apply a version override to a specific module of a multi-module build. For example to apply an explicit dependency override only to module `B` of project `foo`.

    gradle generateAlignmentMetadata -DdependencyOverride.junit:junit@org.foo:moduleB=4.10


#### Per-Module Prevention of Override

It is also possible to **prevent overriding dependency versions** on a per module basis:

    gradle generateAlignmentMetadata -DdependencyOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=

For example:

    gradle generateAlignmentMetadata -DdependencyOverride.junit:junit@org.foo:moduleB=

#### Override Prevention with Wildcards

Likewise, you can prevent overriding a dependency version across the entire project using a wildcard:

    gradle generateAlignmentMetadata -DdependencyOverride.[groupId]:[artifactId]@*=

For example:

    gradle generateAlignmentMetadata -DdependencyOverride.junit:junit@*=

Or, you can prevent overriding a dependency version across the entire project where the `groupId` matches, using multiple wildcards:

    gradle generateAlignmentMetadata -DdependencyOverride.[groupId]:*@*=

For example:

    gradle generateAlignmentMetadata -DdependencyOverride.junit:*@*=

#### Per Module Override Prevention with Wildcards

Linking the two prior concepts it is also possible to prevent overriding using wildcards on a per-module basis e.g.

    gradle generateAlignmentMetadata -DdependencyOverride.*:*@org.foo:moduleB=

This will prevent any alignment within the `org.foo:moduleB`.

    gradle generateAlignmentMetadata -DdependencyOverride.*:*@org.foo:*=

This will prevent any alignment within `org.foo` and all sub-modules within that.

### Direct/Transitive Dependencies

By default GME will _only_ align direct dependencies and not transitive as well. There are scenarios - such as using the [Shadow Plugin](https://github.com/johnrengelman/shadow) when creating a shaded jar that is may be desirable to align transitive as well. In that case set `overrideTransitive=true`. Note that by default it is **implicitly** set to false, but if the Shadow Plugin is detected and the user has **not** explicitly configured `overrideTransitive` then an exception will be thrown.

### Gradle Lock Files

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Lock file handling has been significantly improved with GME 3.5.
</td>
</tr>
</table>

[Gradle lock files](https://docs.gradle.org/current/userguide/dependency_locking.html) are supported in GME. The tool will update the lock files with the new aligned versions. A configuration option `lenientLockMode` (default: false) will also adjust any occurences of `dependencyLocking {` to add `lockMode = LockMode.LENIENT` if the option is set to true. This is useful to avoid some lock file issues after alignment.
