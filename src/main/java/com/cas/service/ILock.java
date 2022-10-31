package com.cas.service;

/**
 * @author xiang_long
 * @version 1.0
 * @date 2022/10/31 2:26 下午
 * @desc
 */
public interface ILock {

    boolean tryLock(long timeoutSec);

    void unLock();

}
