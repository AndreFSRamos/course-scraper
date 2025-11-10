INSERT INTO platform (name, base_url, enabled) VALUES
  ('evg',   'https://www.escolavirtual.gov.br', TRUE),
  ('fgv',   'https://educacao-executiva.fgv.br', TRUE),
  ('sebrae','https://www.sebrae.com.br',        TRUE)
ON CONFLICT (name) DO UPDATE
SET base_url = EXCLUDED.base_url,
    enabled  = EXCLUDED.enabled;
