package com.stream.service;

public interface CrawlerService {

    void startService();
    void stopService();
    int steamDiscountCrawler(int limit);
    int steamTopSellerCrawler(int limit);
    int epicFreeGamesCrawler();
}
