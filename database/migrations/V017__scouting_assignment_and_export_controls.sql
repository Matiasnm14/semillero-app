ALTER TABLE scouting_assignments
    ADD COLUMN IF NOT EXISTS criteria_set_id uuid REFERENCES scouting_criteria_sets(id);

ALTER TABLE scouting_assignments
    ADD COLUMN IF NOT EXISTS scheduled_at timestamptz;

WITH unambiguous_versions AS (
    SELECT assignment_id, (array_agg(criteria_set_id))[1] AS criteria_set_id
    FROM scouting_observation_reports
    WHERE assignment_id IS NOT NULL
    GROUP BY assignment_id
    HAVING count(DISTINCT criteria_set_id) = 1
)
UPDATE scouting_assignments a
SET criteria_set_id = versions.criteria_set_id,
    form_version = criteria.version::varchar
FROM unambiguous_versions versions
JOIN scouting_criteria_sets criteria ON criteria.id = versions.criteria_set_id
WHERE a.id = versions.assignment_id AND a.criteria_set_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_scouting_assignments_criteria_set
    ON scouting_assignments(criteria_set_id);

ALTER TABLE scouting_exports
    ADD COLUMN IF NOT EXISTS reason varchar(500) NOT NULL DEFAULT 'Exportación anterior a V017';

ALTER TABLE scouting_exports
    ADD COLUMN IF NOT EXISTS selected_fields jsonb NOT NULL DEFAULT '["profile","history","tournamentContext"]'::jsonb;
