package com.project.uptimely.uptimely1.repository;

public interface CheckStats {
    long getTotal();
    Long getFailed();
    Double getAvgResponseMs();
}
