package net.citotech.cito.Model;

/**
 *
 * @author josephtabajjwa
 */
public class KeyPairStrings {
    String public_key;
    String private_key;

    public KeyPairStrings(String public_key, String private_key) {
        this.public_key = public_key;
        this.private_key = private_key;
    }
    
    

    public String getPublic_key() {
        return public_key;
    }

    public void setPublic_key(String public_key) {
        this.public_key = public_key;
    }

    public String getPrivate_key() {
        return private_key;
    }

    public void setPrivate_key(String private_key) {
        this.private_key = private_key;
    }
    
    
}

