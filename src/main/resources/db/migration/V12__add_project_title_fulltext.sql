ALTER TABLE project_tb ADD FULLTEXT INDEX ft_project_title (project_title) WITH PARSER ngram;
DROP INDEX idx_project_title ON project_tb;
