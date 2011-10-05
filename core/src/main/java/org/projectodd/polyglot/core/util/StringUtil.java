package org.projectodd.polyglot.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author Anthony Eden
 * @author Bob McWhirter
 */
public class StringUtil {

    public static String underscore(String word) {
        String firstPattern = "([A-Z]+)([A-Z][a-z])";
        String secondPattern = "([a-z\\d])([A-Z])";
        String replacementPattern = "$1_$2";
        word = word.replaceAll( "\\.", "/" ); // replace package separator with
                                              // slash
        word = word.replaceAll( "::", "/" ); // replace package separator with
                                             // slash
        word = word.replaceAll( "\\$", "__" ); // replace $ with two underscores
                                               // for inner classes
        word = word.replaceAll( firstPattern, replacementPattern ); // replace
                                                                    // capital
                                                                    // letter
                                                                    // with
                                                                    // _ plus
                                                                    // lowercase
                                                                    // letter
        word = word.replaceAll( secondPattern, replacementPattern );
        word = word.replace( '-', '_' );
        word = word.toLowerCase();
        return word;
    }

    public static String camelize(String str) {
        Pattern p = Pattern.compile( "\\/(.?)" );
        Matcher m = p.matcher( str );
        while (m.find()) {
            str = m.replaceFirst( "::" + m.group( 1 ).toUpperCase() );
            m = p.matcher( str );
        }
    
        p = Pattern.compile( "(_)(.)" );
        m = p.matcher( str );
        while (m.find()) {
            str = m.replaceFirst( m.group( 2 ).toUpperCase() );
            m = p.matcher( str );
        }
    
        if (str.length() > 0) {
            str = str.substring( 0, 1 ).toUpperCase() + str.substring( 1 );
        }
        return str;
    }

}
