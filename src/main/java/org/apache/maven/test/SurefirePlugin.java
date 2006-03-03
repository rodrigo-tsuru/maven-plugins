package org.apache.maven.test;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.surefire.booter.ForkConfiguration;
import org.apache.maven.surefire.booter.SurefireBooter;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.booter.SurefireExecutionException;
import org.apache.maven.surefire.report.BriefConsoleReporter;
import org.apache.maven.surefire.report.BriefFileReporter;
import org.apache.maven.surefire.report.ConsoleReporter;
import org.apache.maven.surefire.report.DetailedConsoleReporter;
import org.apache.maven.surefire.report.FileReporter;
import org.apache.maven.surefire.report.ForkingConsoleReporter;
import org.apache.maven.surefire.report.XMLReporter;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Run tests using Surefire.
 *
 * @author Jason van Zyl
 * @version $Id$
 * @requiresDependencyResolution test
 * @goal test
 * @phase test
 */
public class SurefirePlugin
    extends AbstractMojo
{
    /**
     * Set this to 'true' to bypass unit tests entirely. Its use is NOT RECOMMENDED, but quite convenient on occasion.
     *
     * @parameter expression="${maven.test.skip}"
     */
    private boolean skip;

    /**
     * Set this to true to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on occasion.
     *
     * @parameter expression="${maven.test.failure.ignore}"
     */
    private boolean testFailureIgnore;

    /**
     * The base directory of the project being tested. This can be obtained in your unit test by System.getProperty("basedir").
     *
     * @parameter expression="${basedir}"
     * @required
     */
    private File basedir;

    /**
     * The directory containing generated classes of the project being tested.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File classesDirectory;

    /**
     * The directory containing generated test classes of the project being tested.
     *
     * @parameter expression="${project.build.testOutputDirectory}"
     * @required
     */
    private File testClassesDirectory;

    /**
     * The classpath elements of the project being tested.
     *
     * @parameter expression="${project.testClasspathElements}"
     * @required
     * @readonly
     */
    private List classpathElements;

    /**
     * Base directory where all reports are written to.
     *
     * @parameter expression="${project.build.directory}/surefire-reports"
     */
    private File reportsDirectory;

    /**
     * The test source directory containing test class sources.
     *
     * @parameter expression="${project.build.testSourceDirectory}"
     * @required
     */
    private File testSourceDirectory;

    /**
     * Specify this parameter if you want to use the test pattern matching notation, Ant pattern matching, to select tests to run.
     * The Ant pattern will be used to create an include pattern formatted like <code>**&#47;${test}.java</code>
     * When used, the <code>includes</code> and <code>excludes</code> patterns parameters are ignored
     *
     * @parameter expression="${test}"
     */
    private String test;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be included in testing.
     * When not specified and whent the <code>test</code> parameter is not specified, the default includes will be
     * <code>**&#47;Test*.java   **&#47;*Test.java   **&#47;*TestCase.java</code>
     *
     * @parameter
     */
    private List includes;

    /**
     * List of patterns (separated by commas) used to specify the tests that should be excluded in testing.
     * When not specified and whent the <code>test</code> parameter is not specified, the default excludes will be
     * <code>**&#47;Abstract*Test.java  **&#47;Abstract*TestCase.java **&#47;*$*</code>
     *
     * @parameter
     */
    private List excludes;

    /**
     * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use System.setProperty( "localRepository").
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * List of System properties to pass to the JUnit tests.
     *
     * @parameter
     */
    private Properties systemProperties;

    /**
     * Map of of plugin artifacts.
     *
     * @parameter expression="${plugin.artifactMap}"
     * @required
     * @readonly
     */
    private Map pluginArtifactMap;

    /**
     * Map of of project artifacts.
     *
     * @parameter expression="${project.artifactMap}"
     * @required
     * @readonly
     */
    private Map projectArtifactMap;

    /**
     * Option to print summary of test suites or just print the test cases that has errors.
     *
     * @parameter expression="${surefire.printSummary}"
     * default-value="true"
     */
    private boolean printSummary;

    /**
     * Selects the formatting for the test report to be generated.  Can be set as brief, plain, or xml.
     *
     * @parameter expression="${surefire.reportFormat}"
     * default-value="brief"
     */
    private String reportFormat;

    /**
     * Option to generate a file test report or just output the test report to the console.
     *
     * @parameter expression="${surefire.useFile}"
     * default-value="true"
     */
    private boolean useFile;

    /**
     * Option to specify the forking mode. Can be "none", "once" or "pertest"
     *
     * @parameter expression="${forkMode}"
     * default-value="none"
     */
    private String forkMode;

    /**
     * Option to specify the jvm (or path to the java executable) to use with
     * the forking options. For the default we will assume that java is in the path.
     *
     * @parameter expression="${jvm}"
     * default-value="java"
     */
    private String jvm;

    /**
     * Arbitrary options to set on the command line.
     *
     * @parameter expression="${argLine}"
     */
    private String argLine;

    /**
     * Additional environments to set on the command line.
     *
     * @parameter
     */
    private Map environmentVariables = new HashMap();

    /**
     * Command line working directory.
     *
     * @parameter
     */
    private File workingDirectory;

    /**
     * Whether to run the tests in an isolated classloader, or to delegate to the system classloader when forking.
     *
     * @parameter expression="${childDelegation}"
     * default-value="true"
     */
    private boolean childDelegation;

    /**
     * Groups for this test. Only classes/methods/etc decorated with one of the
     * groups specified here will be included in test run, if specified.
     *
     * @parameter expression="${groups}"
     */
    private String groups;

    /**
     * Excluded groups. Any methods/classes/etc with one of the groups specified in this
     * list will specifically not be run.
     *
     * @parameter expression="${excludedGroups}"
     */
    private String excludedGroups;

    /**
     * List of TestNG suite xml file locations, seperated by commas. It should be noted that
     * if suiteXmlFiles is specified, <b>no</b> other tests will be run, ignoring other parameters,
     * like includes and excludes.
     *
     * @parameter
     */
    private File[] suiteXmlFiles;

    /**
     * The attribute thread-count allows you to specify how many threads should be allocated
     * for this execution. Only makes sense to use in conjunction with parallel.
     *
     * @parameter expression="${threadCount}"
     * default-value="0"
     */
    private int threadCount;

    /**
     * When you use the parallel attribute, TestNG will try to run all your test methods in
     * separate threads, except for methods that depend on each other, which will be run in
     * the same thread in order to respect their order of execution.
     *
     * @parameter expression="${parallel}"
     * default-value="false"
     * @todo test how this works with forking, and console/file output parallelism
     */
    private boolean parallel;

    /**
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List remoteRepositories;

    /**
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    private static final String BRIEF_REPORT_FORMAT = "brief";

    private static final String PLAIN_REPORT_FORMAT = "plain";

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().info( "Tests are skipped." );
        }
        else if ( !testClassesDirectory.exists() )
        {
            getLog().info( "No tests to run." );
        }
        else
        {
            SurefireBooter surefireBooter = constructSurefireBooter();

            getLog().info( "Surefire report directory: " + reportsDirectory );

            boolean success;
            try
            {
                success = surefireBooter.run();
            }
            catch ( SurefireBooterForkException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            catch ( SurefireExecutionException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }

            if ( !success )
            {
                String msg = "There are test failures.";

                if ( testFailureIgnore )
                {
                    getLog().error( msg );
                }
                else
                {
                    throw new MojoFailureException( msg );
                }
            }
        }
    }

    private SurefireBooter constructSurefireBooter()
        throws MojoExecutionException
    {
        SurefireBooter surefireBooter = new SurefireBooter();

/* TODO
        surefireBooter.setTestSourceDirectory( testSourceDirectory.getPath() );
*/

        Artifact surefireArtifact = (Artifact) pluginArtifactMap.get( "org.apache.maven.surefire:surefire-booter" );

        if ( surefireArtifact == null )
        {
            throw new MojoExecutionException( "Unable to locate surefire-booter in the list of plugin artifacts" );
        }

        Artifact junitArtifact;
        Artifact testNgArtifact;
        try
        {
            addArtifact( surefireBooter, surefireArtifact );

            junitArtifact = (Artifact) projectArtifactMap.get( "junit:junit" );

            // TODO: this is pretty manual, but I'd rather not require the plugin > dependencies section right now
            testNgArtifact = (Artifact) projectArtifactMap.get( "org.testng:testng" );

            if ( testNgArtifact != null )
            {
                addProvider( surefireBooter, "surefire-testng", surefireArtifact.getBaseVersion() );
            }
            else
            {
                // only need to discover JUnit if there is no TestNG, it runs the tests for you.
                if ( junitArtifact != null )
                {
                    addProvider( surefireBooter, "surefire-junit", surefireArtifact.getBaseVersion() );
                }
            }
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new MojoExecutionException(
                "Unable to locate required surefire provider dependency: " + e.getMessage(), e );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Error to resolving surefire provider dependency: " + e.getMessage(), e );
        }

        if ( suiteXmlFiles != null && suiteXmlFiles.length > 0 )
        {
            if ( testNgArtifact == null )
            {
                throw new MojoExecutionException( "suiteXmlFiles is configured, but there is no TestNG dependency" );
            }
            for ( int i = 0; i < suiteXmlFiles.length; i++ )
            {
                File file = suiteXmlFiles[i];
                if ( file.exists() )
                {
                    surefireBooter.addTestSuite( "org.apache.maven.surefire.testng.TestNgXmlTestSuite",
                                                 new Object[]{file} );
                }
            }
        }
        else
        {
            List includes;
            List excludes;

            if ( test != null )
            {
                // Check to see if we are running a single test. The raw parameter will
                // come through if it has not been set.

                // FooTest -> **/FooTest.java

                includes = new ArrayList();

                excludes = new ArrayList();

                String[] testRegexes = StringUtils.split( test, "," );

                for ( int i = 0; i < testRegexes.length; i++ )
                {
                    includes.add( "**/" + testRegexes[i] + ".java" );
                }
            }
            else
            {
                includes = this.includes;

                excludes = this.excludes;

                // defaults here, qdox doesn't like the end javadoc value
                // Have to wrap in an ArrayList as surefire expects an ArrayList instead of a List for some reason
                if ( includes == null || includes.size() == 0 )
                {
                    includes = new ArrayList(
                        Arrays.asList( new String[]{"**/Test*.java", "**/*Test.java", "**/*TestCase.java"} ) );
                }
                if ( excludes == null || excludes.size() == 0 )
                {
                    excludes = new ArrayList(
                        Arrays.asList( new String[]{"**/Abstract*Test.java", "**/Abstract*TestCase.java", "**/*$*"} ) );
                }
            }

            if ( testNgArtifact != null )
            {
                surefireBooter.addTestSuite( "org.apache.maven.surefire.testng.TestNGDirectoryTestSuite", new Object[]{
                    testClassesDirectory, includes, excludes, groups, excludedGroups, Boolean.valueOf( parallel ),
                    new Integer( threadCount )} );
            }
            else if ( junitArtifact != null )
            {
                surefireBooter.addTestSuite( "org.apache.maven.surefire.junit.JUnitDirectoryTestSuite",
                                             new Object[]{testClassesDirectory, includes, excludes} );
            }
            else
            {
                throw new MojoExecutionException( "No Java test frameworks found" );
            }
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        getLog().debug( "Test Classpath :" );

        getLog().debug( "  " + testClassesDirectory.getPath() );

        surefireBooter.addClassPathUrl( testClassesDirectory.getPath() );

        getLog().debug( "  " + classesDirectory.getPath() );

        surefireBooter.addClassPathUrl( classesDirectory.getPath() );

        for ( Iterator i = classpathElements.iterator(); i.hasNext(); )
        {
            String classpathElement = (String) i.next();

            getLog().debug( "  " + classpathElement );

            surefireBooter.addClassPathUrl( classpathElement );
        }

        // ----------------------------------------------------------------------
        // Forking
        // ----------------------------------------------------------------------

        ForkConfiguration fork = new ForkConfiguration();

        fork.setForkMode( forkMode );

        processSystemProperties( fork.isForking() );

        if ( getLog().isDebugEnabled() )
        {
            showMap( systemProperties, "system property" );
        }

        if ( fork.isForking() )
        {
            fork.setSystemProperties( systemProperties );

            fork.setJvmExecutable( jvm );

            if ( workingDirectory != null )
            {
                fork.setWorkingDirectory( workingDirectory );
            }
            else
            {
                fork.setWorkingDirectory( basedir );
            }

            fork.setArgLine( argLine );

            fork.setEnvironmentVariables( environmentVariables );

            fork.setChildDelegation( childDelegation );

            if ( getLog().isDebugEnabled() )
            {
                showMap( environmentVariables, "environment variable" );

                fork.setDebug( true );
            }
        }

        surefireBooter.setForkConfiguration( fork );

        addReporters( surefireBooter, fork.isForking() );

        return surefireBooter;
    }

    private void showMap( Map map, String setting )
    {
        for ( Iterator i = map.keySet().iterator(); i.hasNext(); )
        {
            String key = (String) i.next();
            String value = (String) map.get( key );
            getLog().debug( "Setting " + setting + " [" + key + "]=[" + value + "]" );
        }
    }

    private void addProvider( SurefireBooter surefireBooter, String provider, String version )
        throws ArtifactNotFoundException, ArtifactResolutionException
    {
        Artifact providerArtifact = artifactFactory.createDependencyArtifact( "org.apache.maven.surefire", provider,
                                                                              VersionRange.createFromVersion( version ),
                                                                              "jar", null, Artifact.SCOPE_TEST );
        resolveArtifact( providerArtifact, surefireBooter );
    }

    private void resolveArtifact( Artifact providerArtifact, SurefireBooter surefireBooter )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        Artifact originatingArtifact = artifactFactory.createBuildArtifact( "dummy", "dummy", "1.0", "jar" );

        ArtifactResolutionResult result = artifactResolver.resolveTransitively(
            Collections.singleton( providerArtifact ), originatingArtifact, localRepository, remoteRepositories,
            metadataSource, null );

        for ( Iterator i = result.getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();

            getLog().debug( "Adding to surefire test classpath: " + artifact.getFile().getAbsolutePath() );

            surefireBooter.addSurefireClassPathUrl( artifact.getFile().getAbsolutePath() );
        }
    }

    private void addArtifact( SurefireBooter surefireBooter, Artifact artifact )
        throws ArtifactNotFoundException, ArtifactResolutionException
    {
        resolveArtifact( artifact, surefireBooter );
    }

    protected void processSystemProperties( boolean setInSystem )
    {
        if ( systemProperties == null )
        {
            systemProperties = new Properties();
        }

        systemProperties.setProperty( "basedir", basedir.getAbsolutePath() );

        systemProperties.setProperty( "localRepository", localRepository.getBasedir() );

        if ( setInSystem )
        {
            // Add all system properties configured by the user
            Iterator iter = systemProperties.keySet().iterator();

            while ( iter.hasNext() )
            {
                String key = (String) iter.next();

                String value = systemProperties.getProperty( key );

                System.setProperty( key, value );
            }
        }
    }

    /**
     * <p> Adds Reporters that will generate reports with different formatting.
     * <p> The Reporter that will be added will be based on the value of the parameter
     * useFile, reportFormat, and printSummary.
     *
     * @param surefireBooter The surefire booter that will run tests.
     * @param forking
     */
    private void addReporters( SurefireBooter surefireBooter, boolean forking )
    {
        if ( useFile )
        {
            if ( printSummary )
            {
                if ( forking )
                {
                    surefireBooter.addReport( ForkingConsoleReporter.class.getName() );
                }
                else
                {
                    surefireBooter.addReport( ConsoleReporter.class.getName() );
                }
            }

            if ( BRIEF_REPORT_FORMAT.equals( reportFormat ) )
            {
                surefireBooter.addReport( BriefFileReporter.class.getName(), new Object[]{reportsDirectory} );
            }
            else if ( PLAIN_REPORT_FORMAT.equals( reportFormat ) )
            {
                surefireBooter.addReport( FileReporter.class.getName(), new Object[]{reportsDirectory} );
            }
        }
        else
        {
            if ( BRIEF_REPORT_FORMAT.equals( reportFormat ) )
            {
                surefireBooter.addReport( BriefConsoleReporter.class.getName() );
            }
            else if ( PLAIN_REPORT_FORMAT.equals( reportFormat ) )
            {
                surefireBooter.addReport( DetailedConsoleReporter.class.getName() );
            }
        }

        surefireBooter.addReport( XMLReporter.class.getName(), new Object[]{reportsDirectory} );
    }
}
