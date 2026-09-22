package com.preonsurl.apis.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Slf4j
@Service
public class DefaultDnsVerificationService implements DnsVerificationService {

    @Override
    public String resolveCname(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }

        String normalizedDomain = domain.trim().toLowerCase();
        if (normalizedDomain.endsWith(".")) {
            normalizedDomain = normalizedDomain.substring(0, normalizedDomain.length() - 1);
        }

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            DirContext dirContext = new InitialDirContext(env);
            Attributes attrs = dirContext.getAttributes(normalizedDomain, new String[]{"CNAME"});

            if (attrs != null) {
                Attribute cnameAttr = attrs.get("CNAME");
                if (cnameAttr != null) {
                    NamingEnumeration<?> all = cnameAttr.getAll();
                    if (all.hasMore()) {
                        Object target = all.next();
                        if (target != null) {
                            String targetStr = target.toString().trim().toLowerCase();
                            if (targetStr.endsWith(".")) {
                                targetStr = targetStr.substring(0, targetStr.length() - 1);
                            }
                            return targetStr;
                        }
                    }
                }
            }
        } catch (NamingException e) {
            log.debug("DNS lookup for CNAME of '{}' returned no record or timed out: {}", normalizedDomain, e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error during DNS CNAME lookup for '{}': {}", normalizedDomain, e.getMessage());
        }
        return null;
    }
}
