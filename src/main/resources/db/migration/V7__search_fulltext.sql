-- 전체 검색용 FULLTEXT. 불용어 목록을 끄고 다시 만든다 — 켜 두면 ngram 조각 at · is 등이 색인에서 빠져 data 같은 영어 단어를 찾지 못한다
-- 이 설정은 색인을 만들 때만 쓰여 서버 설정은 바꾸지 않아도 된다
SET SESSION innodb_ft_enable_stopword = OFF;

DROP INDEX ft_question_title_body ON question;
CREATE FULLTEXT INDEX ft_question_title_body ON question (title, body) WITH PARSER ngram;

DROP INDEX ft_share_title_body ON share;
CREATE FULLTEXT INDEX ft_share_title_body ON share (title, body) WITH PARSER ngram;

-- 이름도 검색 대상이라 색인에 넣는다
DROP INDEX ft_member_nickname_status_note ON member;
CREATE FULLTEXT INDEX ft_member_search ON member (nickname, name, status_note) WITH PARSER ngram;

CREATE FULLTEXT INDEX ft_schedule_search ON schedule (title, memo, place) WITH PARSER ngram;

SET SESSION innodb_ft_enable_stopword = ON;
