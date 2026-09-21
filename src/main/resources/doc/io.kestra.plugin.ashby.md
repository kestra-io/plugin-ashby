This plugin allows you to integrate Kestra with the [Ashby](https://developers.ashbyhq.com/) ATS and recruiting platform.

With this plugin, you can orchestrate your recruiting operations by interacting directly with the Ashby API from your Kestra workflows.

## Prerequisites

To use this plugin, you will need an **Ashby API Key**.
You can create one by navigating to your Ashby Admin settings and generating an API key with the necessary permissions for the resources you intend to access.

## Using the Plugin

All tasks require your Ashby API Key for authentication. It is highly recommended to store your API key in Kestra Secrets.

For example, to list all job postings:

```yaml
id: fetch_job_postings
namespace: company.team

tasks:
  - id: list_job_postings
    type: io.kestra.plugin.ashby.jobpostings.List
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
```
