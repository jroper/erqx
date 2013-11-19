/*
 * Copyright (c) 2003-2011, Simon Brown
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *   - Neither the name of Pebble nor the names of its contributors may
 *     be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package migration;

import org.radeox.api.engine.RenderEngine;
import org.radeox.api.engine.WikiRenderEngine;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.engine.BaseRenderEngine;
import org.radeox.engine.context.BaseInitialRenderContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorates blog entries and comments by rendering them with Radeox, internal
 * links pointing to static pages within the blog.
 *
 * @author Simon Brown
 */
public class RadeoxRenderer {

    private static final String WIKI_START_TAG = "<wiki>";
    private static final String WIKI_END_TAG = "</wiki>";

    public static String wikify(String content) {
        InitialRenderContext renderContext = new BaseInitialRenderContext();
        RenderEngine renderEngine = new RadeoxWikiRenderEngine(renderContext);
        // is there work to do?
        if (content == null || content.length() == 0) {
            return "";
        }

        // this pattern says "take the shortest match you can find where there are
        // one or more characters between wiki tags"
        //  - the match is case insensitive and DOTALL means that newlines are
        //  - considered as a character match
        Pattern p = Pattern.compile(WIKI_START_TAG + ".+?" + WIKI_END_TAG,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(content);

        // while there are blocks to be escaped
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            // grab the text, strip off the escape tags and transform it
            String textToWikify = content.substring(start, end);
            textToWikify = textToWikify.substring(WIKI_START_TAG.length(), textToWikify.length() - WIKI_END_TAG.length());
            textToWikify = renderEngine.render(textToWikify, renderContext);

            // now add it back into the original text
            content = content.substring(0, start) + textToWikify + content.substring(end, content.length());
            m = p.matcher(content);
        }

        return content;
    }

}

class RadeoxWikiRenderEngine extends BaseRenderEngine implements WikiRenderEngine {

    public RadeoxWikiRenderEngine(InitialRenderContext context) {
        super(context);
        context.setRenderEngine(this);
    }

    public boolean exists(String name) {
        return false;
    }

    public boolean showCreate() {
        return false;
    }

    public void appendLink(StringBuffer buffer, String name, String view) {
        throw new UnsupportedOperationException();
    }

    public void appendLink(StringBuffer buffer, String name, String view, String anchor) {
        throw new UnsupportedOperationException();
    }

    public void appendCreateLink(StringBuffer buffer, String name, String view) {
        throw new UnsupportedOperationException();
    }
}