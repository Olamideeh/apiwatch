package com.example.apiwatch.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface HostAddressResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}