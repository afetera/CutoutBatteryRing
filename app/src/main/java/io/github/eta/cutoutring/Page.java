package io.github.eta.cutoutring;

import android.view.LayoutInflater;
import android.view.View;

/** 底部导航里的一个页面。 */
public abstract class Page {

    protected final MainActivity host;
    protected final View root;

    Page(MainActivity host, int layoutRes) {
        this.host = host;
        this.root = LayoutInflater.from(host).inflate(layoutRes, null);
    }

    public View getView() {
        return root;
    }

    /** 切到本页时调用。 */
    public void onShown() {
    }

    /** 切走时调用。 */
    public void onHidden() {
    }
}
