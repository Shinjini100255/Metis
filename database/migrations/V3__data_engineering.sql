-- Forward-only: preserve existing prices, migrations and run history.
ALTER TABLE ingestion_runs ADD COLUMN records_updated integer NOT NULL DEFAULT 0,
 ADD COLUMN records_skipped integer NOT NULL DEFAULT 0,
 ADD COLUMN records_rejected integer NOT NULL DEFAULT 0,
 ADD COLUMN request_count integer NOT NULL DEFAULT 0,
 ADD COLUMN quality_report jsonb;
CREATE TABLE provider_requests (
 id bigserial PRIMARY KEY, ingestion_run_id bigint REFERENCES ingestion_runs(id),
 provider varchar(60) NOT NULL, requested_at timestamptz NOT NULL DEFAULT now(),
 outcome varchar(40) NOT NULL DEFAULT 'ATTEMPTED'
);
CREATE INDEX provider_requests_budget ON provider_requests(provider,requested_at DESC);
