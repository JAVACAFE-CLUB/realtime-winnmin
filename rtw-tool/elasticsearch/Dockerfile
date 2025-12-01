FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# Nori (한국어 형태소 분석기) 플러그인 설치
RUN bin/elasticsearch-plugin install analysis-nori

# ICU 분석기 (유니코드 정규화) - 선택사항
# RUN bin/elasticsearch-plugin install analysis-icu
