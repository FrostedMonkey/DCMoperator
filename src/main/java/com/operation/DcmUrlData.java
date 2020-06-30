package com.operation;

import org.dcm4che3.net.Connection;

import java.util.StringTokenizer;

public class DcmUrlData {
    public static final int DICOM_PORT = 104;
    private static final int DELIMITER = -1;
    private static final int CALLED_AET = 0;
    private static final int CALLING_AET = 1;
    private static final int HOST = 2;
    private static final int PORT = 3;
    private static final int END = 4;
    private Connection connection = new Connection();
    private Connection.Protocol protocol;
    private String calledAET;
    private String callingAET;
    private String host;
    private int port = 104;

    public Connection.Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Connection.Protocol protocol) {
        this.protocol = protocol;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public DcmUrlData(String spec, String type) {
        if (type == null) {
            this.parse(spec.trim());
            if (this.calledAET == null) {
                throw new IllegalArgumentException("Missing called AET");
            } else if (this.host == null) {
                throw new IllegalArgumentException("Missing host name");
            }
            connection.setProtocol(this.protocol);
            connection.setPort(this.getPort());
            connection.setHostname(this.getHost());
        } else if(type.equals("SCP")) {
            this.parseLocal(spec);
        }


    }

    public DcmUrlData(String protocol, String calledAET, String callingAET, String host, int port) {
        this.protocol = Connection.Protocol.valueOf(protocol);
        this.calledAET = calledAET;
        this.callingAET = callingAET;
        this.host = host;
        this.port = port;
    }


    public final boolean isTLS() {
        return this.connection.isTls();
    }

    public final String[] getCipherSuites() {
        return this.connection.getTlsCipherSuites();
    }

    public final String getCallingAET() {
        return this.callingAET;
    }

    public final String getCalledAET() {
        return this.calledAET;
    }

    public final String getHost() {
        return this.host;
    }

    public final int getPort() {
        return this.port;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(64);
        sb.append(this.protocol).append("://").append(this.calledAET);
        if (this.callingAET != null) {
            sb.append(':').append(this.callingAET);
        }

        sb.append('@').append(this.host).append(':').append(this.port);
        return sb.toString();
    }

    private void parse(String s) {
        int delimPos = s.indexOf("://");
        if (delimPos == -1) {
            throw new IllegalArgumentException(s);
        } else {
            this.protocol = Connection.Protocol.valueOf(s.substring(0, delimPos));
            StringTokenizer stk = new StringTokenizer(s.substring(delimPos + 3), ":@/", true);
            int state = 0;
            boolean tcpPart = false;

            while (stk.hasMoreTokens()) {
                String tk = stk.nextToken();
                switch (tk.charAt(0)) {
                    case '/':
                        return;
                    case ':':
                        state = tcpPart ? 3 : 1;
                        break;
                    case '@':
                        tcpPart = true;
                        state = 2;
                        break;
                    default:
                        switch (state) {
                            case 0:
                                this.calledAET = tk;
                                break;
                            case 1:
                                this.callingAET = tk;
                                break;
                            case 2:
                                this.host = tk;
                                break;
                            case 3:
                                this.port = Integer.parseInt(tk);
                                return;
                            default:
                                throw new RuntimeException();
                        }

                        state = -1;
                }
            }

        }
    }

    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 解析本地SCPurl
     */
    private void parseLocal(String s) {
        StringTokenizer stk = new StringTokenizer(s, ":@/", true);
        int state = 0;
        while (stk.hasMoreTokens()) {
            String tk = stk.nextToken();
            switch (tk.charAt(0)) {
                case '/':
                    return;
                case ':':
                    state = 2;
                    break;
                case '@':
                    state = 1;
                    break;
                default:
                    switch (state) {
                        case 0:
                            this.calledAET = tk;
                            break;
                        case 1:
                            this.host = tk;
                            break;
                        case 2:
                            this.port = Integer.parseInt(tk);
                            return;
                        default:
                            throw new RuntimeException();
                    }
                    state = -1;
            }
        }
    }
}
