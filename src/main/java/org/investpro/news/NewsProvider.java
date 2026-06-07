package org.investpro.news;

public interface NewsProvider {
    NewsFetchResult fetch(NewsSourceDefinition source);
}
