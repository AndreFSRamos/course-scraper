INSERT INTO platform (name, base_url, enabled) VALUES
  ('evg',   'https://www.escolavirtual.gov.br', 1),
  ('fgv',   'https://educacao-executiva.fgv.br', 1),
  ('sebrae','https://www.sebrae.com.br',        1)
ON DUPLICATE KEY UPDATE base_url=VALUES(base_url), enabled=VALUES(enabled);