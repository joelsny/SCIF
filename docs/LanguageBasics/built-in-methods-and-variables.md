# Built-in Methods and Variables

```
contract ContractImp implements Contract {
    transient bool m_lock;

    constructor() {}

    @public
    @native
    bool trusts(address a, address b) {
        return a == b || b == address(this);
    }

    @private
    @native
    bool bypassLocks(address from) {
        if (m_lock) {
            return trusts(address(this), from);
        } else {
            return true;
        }
    }

    @private
    @native
    bool acquireLock(address l) {
        if (l == address(this)) {
            m_lock = true;
            return true;
        } else {
            return false;
        }
    }

    @private
    @native
    bool releaseLock(address l) {
        if (l == address(this)) {
            m_lock = false;
            return true;
        } else {
            return false;
        }
    }
}



interface ExternallyManagedContract extends ManagedContract {
    @public
    bool directlyTrusts(address trustee);
    @public
    address[] directTrustees();

    @public
    TrustManager trustManager();
    @public
    LockManager lockManager();
}

interface ManagedContract extends Contract {
    @public
    bool addTrust{this}(address trustee);
    @public
    bool revokeTrust{this}(address trustee);
}
```

