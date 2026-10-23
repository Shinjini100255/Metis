CREATE TABLE training_datasets (
 id varchar(64) PRIMARY KEY, asset_id bigint NOT NULL REFERENCES assets(id),
 provider varchar(60) NOT NULL, development_only boolean NOT NULL DEFAULT false,
 metadata jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE training_jobs (
 id varchar(36) PRIMARY KEY, asset_id bigint NOT NULL REFERENCES assets(id),
 status varchar(30) NOT NULL, development_only boolean NOT NULL DEFAULT false,
 configuration jsonb NOT NULL, dataset_id varchar(64) REFERENCES training_datasets(id),
 message text, started_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz
);
CREATE UNIQUE INDEX training_jobs_one_active ON training_jobs((true)) WHERE status IN ('QUEUED','RUNNING');
ALTER TABLE model_versions ADD COLUMN asset_id bigint REFERENCES assets(id),
 ADD COLUMN algorithm varchar(40), ADD COLUMN task varchar(30),
 ADD COLUMN dataset_id varchar(64) REFERENCES training_datasets(id),
 ADD COLUMN metadata jsonb NOT NULL DEFAULT '{}', ADD COLUMN development_only boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX model_versions_artifact ON model_versions(artifact_reference) WHERE artifact_reference IS NOT NULL;
ALTER TABLE model_predictions ADD COLUMN feature_date date,
 ADD COLUMN feature_timestamp timestamptz, ADD COLUMN target_horizon varchar(40),
 ADD COLUMN task varchar(30), ADD COLUMN provider varchar(60),
 ADD COLUMN status varchar(30) NOT NULL DEFAULT 'LEGACY',
 ADD COLUMN actual_return numeric(20,10), ADD COLUMN actual_direction integer,
 ADD COLUMN resolved_at timestamptz, ADD COLUMN data_age_seconds bigint,
 ADD COLUMN development_only boolean NOT NULL DEFAULT false;
ALTER TABLE model_predictions ALTER COLUMN predicted_value TYPE numeric(20,10);
CREATE UNIQUE INDEX model_predictions_feature_unique ON model_predictions(model_version_id,asset_id,feature_date) WHERE feature_date IS NOT NULL;
