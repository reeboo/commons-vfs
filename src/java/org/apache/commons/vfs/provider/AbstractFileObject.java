/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.apache.commons.vfs.provider;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import org.apache.avalon.excalibur.i18n.ResourceManager;
import org.apache.avalon.excalibur.i18n.Resources;
import org.apache.commons.io.IOUtil;
import org.apache.commons.vfs.FileConstants;
import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSelector;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.NameScope;
import org.apache.commons.vfs.FileSystem;

/**
 * A partial file object implementation.
 *
 * @todo Chop this class up - move all the protected methods to several
 *       interfaces, so that structure and content can be separately overridden.
 *
 * @author <a href="mailto:adammurdoch@apache.org">Adam Murdoch</a>
 * @version $Revision: 1.11 $ $Date: 2002/07/05 04:08:17 $
 */
public abstract class AbstractFileObject
    implements FileObject
{
    private static final Resources REZ =
        ResourceManager.getPackageResources( AbstractFileObject.class );

    private static final FileObject[] EMPTY_FILE_ARRAY = {};

    private final FileName name;
    private final AbstractFileSystem fs;
    private DefaultFileContent content;

    // Cached info
    private boolean attached;
    private AbstractFileObject parent;
    private FileType type;
    private FileObject[] children;

    protected AbstractFileObject( final FileName name,
                                  final AbstractFileSystem fs )
    {
        this.name = name;
        this.fs = fs;
    }

    /**
     * Returns the file system this file belongs to.
     */
    public FileSystem getFileSystem()
    {
        return fs;
    }

    /**
     * Returns true if this file is read-only.
     */
    protected boolean isReadOnly()
    {
        return false;
    }

    /**
     * Attaches this file object to its file resource.  This method is called
     * before any of the doBlah() or onBlah() methods.  Sub-classes can use
     * this method to perform lazy initialisation.
     */
    protected void doAttach() throws Exception
    {
    }

    /**
     * Detaches this file object from its file resource.
     *
     * <p>Called when this file is closed, or its type changes.  Note that
     * the file object may be reused later, so should be able to be reattached.
     */
    protected void doDetach()
    {
    }

    /**
     * Determines the type of the file, returns null if the file does not
     * exist.  The return value of this method is cached, so the
     * implementation can be expensive.
     */
    protected abstract FileType doGetType() throws Exception;

    /**
     * Lists the children of the file.  Is only called if {@link #doGetType}
     * returns {@link FileType#FOLDER}.  The return value of this method
     * is cached, so the implementation can be expensive.
     */
    protected abstract String[] doListChildren() throws Exception;

    /**
     * Deletes the file.  Is only called when:
     * <ul>
     * <li>{@link #isReadOnly} returns false.
     * <li>{@link #doGetType} does not return null.
     * <li>This file has no children.
     * </ul>
     */
    protected void doDelete() throws Exception
    {
        final String message = REZ.getString( "delete-not-supported.error" );
        throw new FileSystemException( message );
    }

    /**
     * Creates this file as a folder.  Is only called when:
     * <ul>
     * <li>{@link #isReadOnly} returns false.
     * <li>{@link #doGetType} returns null.
     * <li>The parent folder exists or this file is the root of the file
     *     system.
     * </ul>
     */
    protected void doCreateFolder() throws Exception
    {
        final String message = REZ.getString( "create-folder-not-supported.error" );
        throw new FileSystemException( message );
    }

    /**
     * Creates a local copy of this file.
     */
    protected File doReplicateFile( final FileSelector selector ) throws FileSystemException
    {
        final FileReplicator replicator = fs.getContext().getReplicator();
        return replicator.replicateFile( this, selector );
    }

    /**
     * Called when the children of this file change.
     */
    protected void onChildrenChanged()
    {
    }

    /**
     * Called from {@link DefaultFileContent#getLastModifiedTime}.
     * The default is to just throw an exception so filesystems must
     * override it to use it.
     */
    protected long doGetLastModifiedTime() throws FileSystemException
    {
        final String message = REZ.getString( "get-last-modified-not-supported.error" );
        throw new FileSystemException( message ); 
    }

    /**
     * Called from {@link DefaultFileContent#setLastModifiedTime}.
     * The default is to just throw an exception so filesystems must
     * override it to use it.
     */
    protected void doSetLastModifiedTime( long modtime )
        throws FileSystemException
    {
        final String message = REZ.getString( "set-last-modified-not-supported.error" );
        throw new FileSystemException( message ); 
    }

    /**
     * Called from {@link DefaultFileContent#getAttribute}.
     * The default implementation just returns null so filesystems must
     * override it to use it.
     */
    protected Object doGetAttribute( String atttrName )
        throws FileSystemException
    {
        return null;
    }

    /**
     * Called from {@link DefaultFileContent#setAttribute}.
     * The default is to just throw an exception so filesystems must
     * override it to use it.
     */
    protected void doSetAttribute( String atttrName, Object value )
        throws FileSystemException
    {
        final String message = REZ.getString( "set-attribute-not-supported.error" );
        throw new FileSystemException( message ); 
    }

    /**
     * Called from {@link DefaultFileContent#getCertificates}.
     * The default implementation just returns null so filesystems must
     * override it to use it.
     */
    protected Certificate[] doGetCertificates() throws FileSystemException
    {
        return null;
    }

    /**
     * Returns the size of the file content (in bytes).  Is only called if
     * {@link #doGetType} returns {@link FileType#FILE}.
     */
    protected abstract long doGetContentSize() throws Exception;

    /**
     * Creates an input stream to read the file content from.  Is only called
     * if  {@link #doGetType} returns {@link FileType#FILE}.
     *
     * <p>There is guaranteed never to be more than one stream for this file
     * (input or output) open at any given time.
     *
     * <p>The returned stream does not have to be buffered.
     */
    protected abstract InputStream doGetInputStream() throws Exception;

    /**
     * Creates an output stream to write the file content to.  Is only
     * called if:
     * <ul>
     * <li>This file is not read-only.
     * <li>{@link #doGetType} returns {@link FileType#FILE}, or
     * {@link #doGetType} returns null, and the file's parent exists
     * and is a folder.
     * </ul>
     *
     * <p>There is guaranteed never to be more than one stream for this file
     * (input or output) open at any given time.
     *
     * <p>The returned stream does not have to be buffered.
     */
    protected OutputStream doGetOutputStream() throws Exception
    {
        final String message = REZ.getString( "write-not-supported.error" );
        throw new FileSystemException( message );
    }

    /**
     * Notification of the output stream being closed.
     * TODO - get rid of this.
     */
    protected void doEndOutput() throws Exception
    {
    }

    /**
     * Notification of the input stream being closed.
     * TODO - get rid of this.
     */
    protected void doEndInput() throws Exception
    {
    }

    /**
     * Returns the URI of the file.
     */
    public String toString()
    {
        return name.getURI();
    }

    /**
     * Returns the name of the file.
     */
    public FileName getName()
    {
        return name;
    }

    /**
     * Returns a URL representation of the file.
     */
    public URL getURL() throws MalformedURLException
    {
        final StringBuffer buf = new StringBuffer();
        try
        {
            return (URL)AccessController.doPrivileged(
                new PrivilegedExceptionAction()
                {
                    public Object run() throws MalformedURLException
                    {
                        return new URL( UriParser.extractScheme( name.getURI(), buf ), null, -1,
                                        buf.toString(), new DefaultURLStreamHandler( fs.getContext() ) );
                    }
                } );
        }
        catch ( PrivilegedActionException e )
        {
            throw (MalformedURLException)e.getException();
        }
    }

    /**
     * Determines if the file exists.
     */
    public boolean exists() throws FileSystemException
    {
        attach();
        return ( type != null );
    }

    /**
     * Returns the file's type.
     */
    public FileType getType() throws FileSystemException
    {
        attach();
        if ( type == null )
        {
            final String message = REZ.getString( "get-type-no-exist.error", name );
            throw new FileSystemException( message );
        }
        return type;
    }

    /**
     * Returns the parent of the file.
     */
    public FileObject getParent() throws FileSystemException
    {
        if ( this == fs.getRoot() )
        {
            if (fs.getParentLayer() != null)
            {
                return fs.getParentLayer().getParent();
            }
            else
            {
                // Root file has no parent
                return null;
            }
        }

        // Locate the parent of this file
        if ( parent == null )
        {
            parent = (AbstractFileObject)fs.findFile( name.getParent() );
        }
        return parent;
    }

    /**
     * Returns the children of the file.
     */
    public FileObject[] getChildren() throws FileSystemException
    {
        attach();
        if ( type == null )
        {
            final String message = REZ.getString( "list-children-no-exist.error", name );
            throw new FileSystemException( message );
        }
        if ( type != FileType.FOLDER )
        {
            final String message = REZ.getString( "list-children-not-folder.error", name );
            throw new FileSystemException( message );
        }

        // Use cached info, if present
        if ( children != null )
        {
            return children;
        }

        // List the children
        String[] files;
        try
        {
            files = doListChildren();
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( Exception exc )
        {
            final String message = REZ.getString( "list-children.error", name );
            throw new FileSystemException( message, exc );
        }

        if ( files == null || files.length == 0 )
        {
            // No children
            children = EMPTY_FILE_ARRAY;
        }
        else
        {
            // Create file objects for the children
            children = new FileObject[ files.length ];
            for ( int i = 0; i < files.length; i++ )
            {
                String file = files[ i ];
                children[ i ] = fs.findFile( name.resolveName( file, NameScope.CHILD ) );
            }
        }

        return children;
    }

    /**
     * Returns a child by name.
     */
    public FileObject resolveFile( String name, NameScope scope ) throws FileSystemException
    {
        // TODO - cache children (only if they exist)
        return fs.findFile( this.name.resolveName( name, scope ) );
    }

    /**
     * Finds a file, relative to this file.
     *
     * @param path
     *          The path of the file to locate.  Can either be a relative
     *          path, which is resolved relative to this file, or an
     *          absolute path, which is resolved relative to the file system
     *          that contains this file.
     */
    public FileObject resolveFile( final String path ) throws FileSystemException
    {
        final FileName otherName = name.resolveName( path );
        return fs.findFile( otherName );
    }

    /**
     * Deletes this file, once all its children have been deleted
     */
    private void deleteSelf() throws FileSystemException
    {
        if ( isReadOnly() )
        {
            final String message = REZ.getString( "delete-read-only.error", name );
            throw new FileSystemException( message );
        }

        // Delete the file
        try
        {
            doDelete();
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( Exception exc )
        {
            final String message = REZ.getString( "delete.error", name );
            throw new FileSystemException( message, exc );
        }

        // Update cached info
        updateType();
    }

    /**
     * Deletes this file, and all children.
     */
    public void delete( final FileSelector selector ) throws FileSystemException
    {
        attach();
        if ( type == null )
        {
            // File does not exist
            return;
        }

        // Locate all the files to delete
        ArrayList files = new ArrayList();
        findFiles( selector, true, files );

        // Delete 'em
        final int count = files.size();
        for ( int i = 0; i < count; i++ )
        {
            final AbstractFileObject file = (AbstractFileObject)files.get( i );
            file.attach();

            // If the file is a folder, make sure all its children have been deleted
            if ( file.type == FileType.FOLDER && file.getChildren().length != 0 )
            {
                // Skip
                continue;
            }

            // Delete the file
            file.deleteSelf();
        }
    }

    /**
     * Creates this file, if it does not exist.  Also creates any ancestor
     * files which do not exist.
     */
    public void create( FileType type ) throws FileSystemException
    {
        attach();
        if ( this.type == type )
        {
            // Already exists as correct type
            return;
        }
        if ( this.type != null )
        {
            final String message = REZ.getString( "create-mismatched-type.error", type, name, this.type );
            throw new FileSystemException( message );
        }
        if ( isReadOnly() )
        {
            final String message = REZ.getString( "create-read-only.error", type, name );
            throw new FileSystemException( message );
        }

        // Traverse up the heirarchy and make sure everything is a folder
        FileObject parent = getParent();
        if ( parent != null )
        {
            parent.create( FileType.FOLDER );
        }

        // Create the folder
        try
        {
            if ( type == FileType.FOLDER )
            {
                doCreateFolder();
                children = EMPTY_FILE_ARRAY;
            }
            else if ( type == FileType.FILE )
            {
                OutputStream outStr = doGetOutputStream();
                outStr.close();
                endOutput();
            }
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( Exception exc )
        {
            final String message = REZ.getString( "create.error", type, name );
            throw new FileSystemException( message, exc );
        }

        // Update cached info
        updateType();
    }

    /**
     * Copies another file to this file.
     */
    public void copyFrom( final FileObject file, final FileSelector selector )
        throws FileSystemException
    {
        if ( !file.exists() )
        {
            final String message = REZ.getString( "copy-missing-file.error", file.getName() );
            throw new FileSystemException( message );
        }
        if ( isReadOnly() )
        {
            final String message = REZ.getString( "copy-read-only.error", file.getType(), file.getName(), name );
            throw new FileSystemException( message );
        }

        // Locate the files to copy across
        final ArrayList files = new ArrayList();
        ( (AbstractFileObject)file ).findFiles( selector, false, files );

        // Copy everything across
        final int count = files.size();
        for ( int i = 0; i < count; i++ )
        {
            final FileObject srcFile = (FileObject)files.get( i );

            // Determine the destination file
            final String relPath = file.getName().getRelativeName( srcFile.getName() );
            final FileObject destFile = resolveFile( relPath, NameScope.DESCENDENT_OR_SELF );

            // Clean up the destination file, if necessary
            if ( destFile.exists() && destFile.getType() != srcFile.getType() )
            {
                // The destination file exists, and is not of the same type,
                // so delete it
                // TODO - add a pluggable policy for deleting and overwriting existing files
                destFile.delete( FileConstants.SELECT_ALL );
            }

            // Copy across
            if ( srcFile.getType() == FileType.FILE )
            {
                copyContent( srcFile, destFile );
            }
            else
            {
                destFile.create( FileType.FOLDER );
            }
        }
    }

    /**
     * Creates a temporary local copy of this file, and its descendents.
     */
    public File replicateFile( final FileSelector selector )
        throws FileSystemException
    {
        if ( !exists() )
        {
            final String message = REZ.getString( "copy-missing-file.error", name );
            throw new FileSystemException( message );
        }

        return doReplicateFile( selector );
    }

    /**
     * Copies the content of another file to this file.
     */
    private static void copyContent( final FileObject srcFile,
                                     final FileObject destFile )
        throws FileSystemException
    {
        try
        {
            final InputStream instr = srcFile.getContent().getInputStream();
            try
            {
                // Create the output stream via getContent(), to pick up the
                // validation it does
                final OutputStream outstr = destFile.getContent().getOutputStream();
                try
                {
                    IOUtil.copy( instr, outstr );
                }
                finally
                {
                    IOUtil.shutdownStream( outstr );
                }
            }
            finally
            {
                IOUtil.shutdownStream( instr );
            }
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( final Exception exc )
        {
            final String message = REZ.getString( "copy-file.error", srcFile.getName(), destFile.getName() );
            throw new FileSystemException( message, exc );
        }
    }

    /**
     * Returns true if this is a Folder.
     */
    boolean isFolder()
    {
        return type == FileType.FOLDER;
    }

    /**
     * Returns the file's content.
     */
    public FileContent getContent() throws FileSystemException
    {
        attach();
        if ( content == null )
        {
            content = new DefaultFileContent( this );
        }
        return content;
    }

    /**
     * Closes this file, and its content.
     */
    public void close() throws FileSystemException
    {
        FileSystemException exc = null;

        // Close the content
        if ( content != null )
        {
            try
            {
                content.close();
            }
            catch ( FileSystemException e )
            {
                exc = e;
            }
        }

        // Detach from the file
        if ( attached )
        {
            doDetach();
            attached = false;
            type = null;
            children = null;
        }

        if ( exc != null )
        {
            throw exc;
        }
    }

    /**
     * Prepares this file for writing.  Makes sure it is either a file,
     * or its parent folder exists.  Returns an output stream to use to
     * write the content of the file to.
     */
    public OutputStream getOutputStream() throws FileSystemException
    {
        attach();
        if ( isReadOnly() )
        {
            final String message = REZ.getString( "write-read-only.error", name );
            throw new FileSystemException( message );
        }
        if ( type == FileType.FOLDER )
        {
            final String message = REZ.getString( "write-folder.error", name );
            throw new FileSystemException( message );
        }

        if ( type == null )
        {
            // Does not exist - make sure parent does
            FileObject parent = getParent();
            if ( parent != null )
            {
                parent.create( FileType.FOLDER );
            }
        }

        // Get the raw output stream
        try
        {
            return doGetOutputStream();
        }
        catch ( FileSystemException exc )
        {
            throw exc;
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( Exception exc )
        {
            final String message = REZ.getString( "write.error", name );
            throw new FileSystemException( message, exc );
        }
    }

    /**
     * Attaches to the file.
     */
    private void attach() throws FileSystemException
    {
        if ( attached )
        {
            return;
        }

        try
        {
            // Attach and determine the file type
            doAttach();
            attached = true;
            type = doGetType();
        }
        catch ( FileSystemException exc )
        {
            throw exc;
        }
        catch ( RuntimeException re )
        {
            throw re;
        }
        catch ( Exception exc )
        {
            final String message = REZ.getString( "get-type.error", name );
            throw new FileSystemException( message, exc );
        }

    }

    /**
     * Called when the ouput stream for this file is closed.
     */
    public void endOutput() throws Exception
    {
        updateType();
        doEndOutput();
    }

    /**
     * Update cached info when this file's type changes.
     */
    private void updateType()
    {
        // Notify parent that its child list may no longer be valid
        notifyParent();

        // Detach
        doDetach();
        attached = false;
        type = null;
        children = null;
    }

    /**
     * Notify the parent of a change to its children, when a child is created
     * or deleted.
     */
    private void notifyParent()
    {
        if ( parent == null )
        {
            // Locate the parent, if it is cached
            parent = (AbstractFileObject)fs.getFile( name.getParent() );
        }

        if ( parent != null )
        {
            parent.invalidateChildren();
        }
    }

    /**
     * Notifies a file that children have been created or deleted.
     */
    private void invalidateChildren()
    {
        children = null;
        onChildrenChanged();
    }

    /**
     * Traverses the descendents of this file, and builds a list of selected
     * files.
     */
    void findFiles( final FileSelector selector,
                    final boolean depthwise,
                    final List selected ) throws FileSystemException
    {
        if ( exists() )
        {
            // Traverse starting at this file
            final DefaultFileSelectorInfo info = new DefaultFileSelectorInfo();
            info.setBaseFolder( this );
            info.setDepth( 0 );
            info.setFile( this );
            traverse( info, selector, depthwise, selected );
        }
    }

    /**
     * Traverses a file.
     */
    private void traverse( final DefaultFileSelectorInfo fileInfo,
                           final FileSelector selector,
                           final boolean depthwise,
                           final List selected )
        throws FileSystemException
    {
        // Check the file itself
        final boolean includeFile = selector.includeFile( fileInfo );
        final FileObject file = fileInfo.getFile();

        // Add the file if not doing depthwise traversal
        if ( !depthwise && includeFile )
        {
            selected.add( file );
        }

        // If the file is a folder, traverse it
        if ( file.getType() == FileType.FOLDER && selector.traverseDescendents( fileInfo ) )
        {
            final int curDepth = fileInfo.getDepth();
            fileInfo.setDepth( curDepth + 1 );

            // Traverse the children
            final FileObject[] children = file.getChildren();
            for ( int i = 0; i < children.length; i++ )
            {
                final FileObject child = children[ i ];
                fileInfo.setFile( child );
                traverse( fileInfo, selector, depthwise, selected );
            }

            fileInfo.setFile( file );
            fileInfo.setDepth( curDepth );
        }

        // Add the file if doing depthwise traversal
        if ( depthwise && includeFile )
        {
            selected.add( file );
        }
    }

}
