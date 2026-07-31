# Project instructions

This is a live demo of Claude Code answering plain-English questions about the Olist
Brazilian E-Commerce dataset — data in StarRocks on MinIO — and turning the answers into
**Apache Superset** charts and dashboards, through the StarRocks, MinIO (AIStor), and
Superset MCP servers. The single goal of these instructions is **transparency**: everything
shown should be visibly reasoned from the live data, not recalled or precomputed. There are
deliberately no canned queries, no expected answers, and no schema hints in this repo.

## Show your work on every data question

For any question that requires querying the StarRocks `olist` database:

1. **State the query in prose first** — say what you're about to run and, for joins, name the
   join keys you're using (e.g. "joining `orders` to `order_items` on `order_id`").
2. **Execute it through the MCP tool** — run the actual `read_query` (or other MCP) call so
   the tool invocation and its full SQL are visible in the transcript. Do not collapse,
   paraphrase, or summarize the SQL away.
3. **Answer from the returned rows** — base the answer on the live result set. If a result is
   surprising, show the raw numbers, not just the interpretation.

## Ground truth is the live schema, not memory

The Olist dataset is a well-known public dataset, so treat any recalled knowledge about it as
a *hint to verify*, never as fact:

- Base join keys and column names on the live schema you inspect, not on memory.
- If a query depends on something not provable from the schema alone (key uniqueness,
  cardinality, whether a distinction like `customer_id` vs `customer_unique_id` matters), run
  a quick check (`COUNT(DISTINCT ...)`, sample rows, before/after row counts) rather than
  assume.
- If you do rely on a recalled convention, flag it so it can be checked.

## Building in Superset

Two MCP servers can reach the same data, and they are for different things:

- **`mcp-server-starrocks`** — exploration. Fast, throwaway answers and Plotly charts written
  to `DEMO_Output/`. Use it while working a question out.
- **`superset`** — the deliverable. Datasets, saved charts, and dashboards that live in
  Superset and outlast the conversation. Use it once a question has an answer worth keeping.

When building in Superset:

- **Verify the SQL first, then save it.** Run the query (either MCP server) and look at the
  rows before turning it into a dataset or chart. A chart built on unverified SQL hides its
  own mistakes.
- **A join or aggregate needs a virtual dataset.** `create_virtual_dataset` saves the SQL as a
  chartable dataset; charts are then built on top of it. Name it for what it contains
  (`monthly_revenue_by_state`), not for the chart you have in mind.
- **Say what you're building before you build it** — which dataset, which chart type, which
  metric on which axis, and why that chart type suits the question.
- **Hand back the URLs the tools return.** Never construct a Superset URL by hand; use the
  ones in the tool responses so the links actually resolve.
- **Ask before creating a second dashboard.** If charts belong on a dashboard that already
  exists, add them to it (`add_chart_to_existing_dashboard`) rather than starting a new one.
- **Don't write code to draw charts.** No HTML, no plotting scripts, no embedded JavaScript —
  the point of the demo is that the MCP tools build real Superset objects. The one exception
  is the StarRocks server's own chart tool during exploration.

If a Superset tool rejects a configuration, show the error and what you changed in response.
Working through a rejection in the open is more useful than a chart that appears with no
explanation.
