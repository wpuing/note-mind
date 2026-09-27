package com.notemind.interfaces.chat.vo;

/**
 * 赞踩满意度统计视图对象。
 */
public class ChatFeedbackStatVo {
    private long likeCount;
    private long dislikeCount;
    private double satisfactionRate;

    /** 获取like Count */
    public long getLikeCount() { return likeCount; }
    /** 设置like Count */
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    /** 获取dislike Count */
    public long getDislikeCount() { return dislikeCount; }
    /** 设置dislike Count */
    public void setDislikeCount(long dislikeCount) { this.dislikeCount = dislikeCount; }
    /** 获取satisfaction Rate */
    public double getSatisfactionRate() { return satisfactionRate; }
    /** 设置satisfaction Rate */
    public void setSatisfactionRate(double satisfactionRate) { this.satisfactionRate = satisfactionRate; }
}
