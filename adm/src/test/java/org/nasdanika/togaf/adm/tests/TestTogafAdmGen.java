package org.nasdanika.togaf.adm.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.nasdanika.common.ConsumerFactory;
import org.nasdanika.common.Context;
import org.nasdanika.common.Diagnostic;
import org.nasdanika.common.DiagnosticException;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.NullProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.common.Status;
import org.nasdanika.common.Supplier;
import org.nasdanika.common.resources.BinaryEntityContainer;
import org.nasdanika.common.resources.FileSystemContainer;
import org.nasdanika.diagram.DiagramPackage;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.emf.EmfUtil;
import org.nasdanika.emf.persistence.FeatureCacheAdapter;
import org.nasdanika.exec.ExecPackage;
import org.nasdanika.exec.content.ContentPackage;
import org.nasdanika.exec.resources.Container;
import org.nasdanika.exec.resources.ReconcileAction;
import org.nasdanika.exec.resources.ResourcesFactory;
import org.nasdanika.exec.resources.ResourcesPackage;
import org.nasdanika.flow.FlowPackage;
import org.nasdanika.flow.Package;
import org.nasdanika.flow.util.FlowObjectLoaderSupplier;
import org.nasdanika.html.emf.EObjectActionResolver;
import org.nasdanika.html.flow.FlowActionProviderAdapterFactory;
import org.nasdanika.html.model.app.Action;
import org.nasdanika.html.model.app.AppPackage;
import org.nasdanika.html.model.app.Label;
import org.nasdanika.html.model.app.gen.AppAdapterFactory;
import org.nasdanika.html.model.app.gen.AppGenObjectLoaderSupplier;
import org.nasdanika.html.model.app.gen.Util;
import org.nasdanika.html.model.app.util.ActionProvider;
import org.nasdanika.html.model.bootstrap.BootstrapPackage;
import org.nasdanika.html.model.html.HtmlPackage;
import org.nasdanika.ncore.NcorePackage;
import org.nasdanika.ncore.util.NcoreResourceSet;

import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;

/**
 * Tests of agile flows.
 * @author Pavel
 *
 */
public class TestTogafAdmGen /* extends TestBase */ {
	
	private static final File INSTANCE_MODELS_DIR = new File("instance");
	
	private static final File GENERATED_MODELS_BASE_DIR = new File("target/model-doc");
	private static final File ACTION_MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "actions");
	private static final File RESOURCE_MODELS_DIR = new File(GENERATED_MODELS_BASE_DIR, "resources");
	
	private static final URI INSTANCE_MODELS_URI = URI.createFileURI(INSTANCE_MODELS_DIR.getAbsolutePath() + "/");	
	private static final URI ACTION_MODELS_URI = URI.createFileURI(ACTION_MODELS_DIR.getAbsolutePath() + "/");	
	private static final URI RESOURCE_MODELS_URI = URI.createFileURI(RESOURCE_MODELS_DIR.getAbsolutePath() + "/");	
	
	/**
	 * Generates an instance model, diagnoses, and stores to XMI.
	 * @param name
	 * @param progressMonitor
	 * @throws Exception
	 */
	protected void generateInstanceModel(String name, Context context, ProgressMonitor progressMonitor) throws Exception {	
		URI resourceURI = URI.createFileURI(new File("model/" + name + ".yml").getCanonicalPath()); 
		
		@SuppressWarnings("resource")
		Supplier<EObject> flowSupplier = new FlowObjectLoaderSupplier(resourceURI, context);
		
		org.nasdanika.common.Consumer<EObject> flowConsumer = new org.nasdanika.common.Consumer<EObject>() {

			@Override
			public double size() {
				return 1;
			}

			@Override
			public String name() {
				return "Generating instance model";
			}

			@Override
			public void execute(EObject obj, ProgressMonitor progressMonitor) throws Exception {
				org.nasdanika.flow.Package pkg = (org.nasdanika.flow.Package) obj;
				
				Package instance = pkg.create();
				ResourceSet resourceSet = new NcoreResourceSet();
				resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(org.eclipse.emf.ecore.resource.Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
				
				org.eclipse.emf.ecore.resource.Resource instanceModelResource = resourceSet.createResource(URI.createURI(name + ".xml").resolve(INSTANCE_MODELS_URI));
				instanceModelResource.getContents().add(instance);
				
				org.eclipse.emf.common.util.Diagnostic instanceDiagnostic = org.nasdanika.emf.EmfUtil.resolveClearCacheAndDiagnose(resourceSet, Context.EMPTY_CONTEXT);
				int severity = instanceDiagnostic.getSeverity();
				if (severity != org.eclipse.emf.common.util.Diagnostic.OK) {
					EmfUtil.dumpDiagnostic(instanceDiagnostic, 2, System.err);
				}
				assertThat(severity).isEqualTo(org.eclipse.emf.common.util.Diagnostic.OK);
														
				try {						
					instanceModelResource.save(null);
				} catch (IOException ioe) {
					throw new NasdanikaException(ioe);
				}
			}
		};
		
		try {
			org.nasdanika.common.Diagnostic diagnostic = org.nasdanika.common.Util.call(flowSupplier.then(flowConsumer), progressMonitor);
			if (diagnostic.getStatus() == Status.FAIL || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
			}
			assertThat(diagnostic.getStatus()).isEqualTo(Status.SUCCESS);
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}
		
	}
	
	/**
	 * Loads instance model from previously generated XMI, diagnoses, generates action model.
	 * @throws Exception
	 */
	public void generateActionModel(String name, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet instanceModelsResourceSet = createResourceSet();
		Resource instanceModelResource = instanceModelsResourceSet.getResource(URI.createURI(name + ".xml").resolve(INSTANCE_MODELS_URI), true);

		org.eclipse.emf.common.util.Diagnostic instanceDiagnostic = org.nasdanika.emf.EmfUtil.resolveClearCacheAndDiagnose(instanceModelsResourceSet, Context.EMPTY_CONTEXT);
		int severity = instanceDiagnostic.getSeverity();
		if (severity != org.eclipse.emf.common.util.Diagnostic.OK) {
			EmfUtil.dumpDiagnostic(instanceDiagnostic, 2, System.err);
		}
		assertThat(severity).isEqualTo(org.eclipse.emf.common.util.Diagnostic.OK);
		
		instanceModelsResourceSet.getAdapterFactories().add(new FlowActionProviderAdapterFactory(Context.EMPTY_CONTEXT) {
			
			private void collect(Notifier target, org.eclipse.emf.common.util.Diagnostic source, Collection<org.eclipse.emf.common.util.Diagnostic> accumulator) {
				List<?> data = source.getData();
				if (source.getChildren().isEmpty()
						&& source.getSeverity() > org.eclipse.emf.common.util.Diagnostic.OK 
						&& data != null 
						&& data.size() == 1 
						&& data.get(0) == target) {
					accumulator.add(source);
				}
				for (org.eclipse.emf.common.util.Diagnostic child: source.getChildren()) {
					collect(target, child, accumulator);
				}
			}
			
			protected Collection<org.eclipse.emf.common.util.Diagnostic> getDiagnostic(Notifier target) {
				Collection<org.eclipse.emf.common.util.Diagnostic> ret = new ArrayList<>();
				collect(target, instanceDiagnostic, ret);
				return ret;
			}
			
			private void collect(Notifier target, EStructuralFeature feature, org.eclipse.emf.common.util.Diagnostic source, Collection<org.eclipse.emf.common.util.Diagnostic> accumulator) {
				List<?> data = source.getData();
				if (source.getChildren().isEmpty() 
						&& source.getSeverity() > org.eclipse.emf.common.util.Diagnostic.OK 
						&& data != null 
						&& data.size() > 1 
						&& data.get(0) == target 
						&& data.get(1) == feature) {
					accumulator.add(source);
				}
				for (org.eclipse.emf.common.util.Diagnostic child: source.getChildren()) {
					collect(target, feature, child, accumulator);
				}
			}

			protected Collection<org.eclipse.emf.common.util.Diagnostic> getFeatureDiagnostic(Notifier target, EStructuralFeature feature) {
				Collection<org.eclipse.emf.common.util.Diagnostic> ret = new ArrayList<>();
				collect(target, feature, instanceDiagnostic, ret);
				return ret;
			}
			
		});
		
		ResourceSet actionModelsResourceSet = createResourceSet();
		actionModelsResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(org.eclipse.emf.ecore.resource.Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		org.eclipse.emf.ecore.resource.Resource actionModelResource = actionModelsResourceSet.createResource(URI.createURI(name + ".xml").resolve(ACTION_MODELS_URI));
		
		Map<EObject,Action> registry = new HashMap<>();
		EObject instance = instanceModelResource.getContents().get(0);
		Action rootAction = EObjectAdaptable.adaptTo(instance, ActionProvider.class).execute(registry::put, progressMonitor);
		Context uriResolverContext = Context.singleton(Context.BASE_URI_PROPERTY, URI.createURI("temp://" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/"));
		BiFunction<Label, URI, URI> uriResolver = org.nasdanika.html.model.app.util.Util.uriResolver(rootAction, uriResolverContext);
		Adapter resolver = EcoreUtil.getExistingAdapter(rootAction, EObjectActionResolver.class);
		if (resolver instanceof EObjectActionResolver) {														
			org.nasdanika.html.emf.EObjectActionResolver.Context resolverContext = new org.nasdanika.html.emf.EObjectActionResolver.Context() {

				@Override
				public Action getAction(EObject semanticElement) {
					return registry.get(semanticElement);
				}

				@Override
				public URI resolve(Action action, URI base) {
					return uriResolver.apply(action, base);
				}
				
			};
			((EObjectActionResolver) resolver).execute(resolverContext, progressMonitor);
		}
		actionModelResource.getContents().add(rootAction);

		actionModelResource.save(null);
	}
	
	protected EObject loadObject(
			String resource, 
			java.util.function.Consumer<org.nasdanika.common.Diagnostic> diagnosticConsumer,
			Context context,
			ProgressMonitor progressMonitor) throws Exception {
		
		URI resourceURI = URI.createFileURI(new File(resource).getAbsolutePath());

		// Diagnosing loaded resources. 
		try {
			return Objects.requireNonNull(org.nasdanika.common.Util.call(new AppGenObjectLoaderSupplier(resourceURI, context), progressMonitor, diagnosticConsumer), "Loaded null from: " + resource);
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}		
	}
	
	/**
	 * Generates a resource model from an action model.
	 * @throws Exception
	 */
	public void generateResourceModel(String name, ProgressMonitor progressMonitor) throws Exception {
		java.util.function.Consumer<Diagnostic> diagnosticConsumer = diagnostic -> {
			if (diagnostic.getStatus() == Status.FAIL || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
			}
			assertThat(diagnostic.getStatus()).isEqualTo(Status.SUCCESS);
		};
		
		Context modelContext = Context.singleton("model-name", name);
		String actionsResource = "model/actions.yml";
		Action root = (Action) loadObject(actionsResource, diagnosticConsumer, modelContext, progressMonitor);
		
		Container container = ResourcesFactory.eINSTANCE.createContainer();
		container.setName(name);
		container.setReconcileAction(ReconcileAction.OVERWRITE);
		
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		Resource modelResource = resourceSet.createResource(URI.createURI(name + ".xml").resolve(RESOURCE_MODELS_URI));
		modelResource.getContents().add(container);
		
		String pageTemplateResource = "model/page-template.yml";
		org.nasdanika.html.model.bootstrap.Page pageTemplate = (org.nasdanika.html.model.bootstrap.Page) loadObject(pageTemplateResource, diagnosticConsumer, modelContext, progressMonitor);
		
		Util.generateSite(
				root, 
				pageTemplate,
				container,
				null,
				null,
				Context.EMPTY_CONTEXT,
				progressMonitor);
		
		modelResource.save(null);
	}
	
	/**
	 * Generates files from the previously generated resource model.
	 * @throws Exception
	 */
	public void generateContainer(String name, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet resourceSet = createResourceSet();
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
				
		Resource containerResource = resourceSet.getResource(URI.createURI(name + ".xml").resolve(RESOURCE_MODELS_URI), true);
	
		File outputDir = new File("../docs");		
		BinaryEntityContainer container = new FileSystemContainer(outputDir);
		for (EObject eObject : containerResource.getContents()) {
			Diagnostician diagnostician = new Diagnostician();
			org.eclipse.emf.common.util.Diagnostic diagnostic = diagnostician.validate(eObject);
			assertThat(diagnostic.getSeverity()).isNotEqualTo(org.eclipse.emf.common.util.Diagnostic.ERROR);
			
			// Diagnosing loaded resources. 
			try {
				ConsumerFactory<BinaryEntityContainer> consumerFactory = Objects.requireNonNull(EObjectAdaptable.adaptToConsumerFactory(eObject, BinaryEntityContainer.class), "Cannot adapt to ConsumerFactory");
				Diagnostic callDiagnostic = org.nasdanika.common.Util.call(consumerFactory.create(Context.EMPTY_CONTEXT), container, progressMonitor);
				if (callDiagnostic.getStatus() == Status.FAIL || callDiagnostic.getStatus() == Status.ERROR) {
					System.err.println("***********************");
					System.err.println("*      Diagnostic     *");
					System.err.println("***********************");
					callDiagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
				}
				assertThat(callDiagnostic.getStatus()).isEqualTo(Status.SUCCESS);
			} catch (DiagnosticException e) {
				System.err.println("******************************");
				System.err.println("*      Diagnostic failed     *");
				System.err.println("******************************");
				e.getDiagnostic().dump(System.err, 4, Status.FAIL);
				throw e;
			}
		}

		// Site map
		String domain = "https://docs.nasdanika.org/togaf";
		WebSitemapGenerator wsg = new WebSitemapGenerator(domain, outputDir);
		BiConsumer<File, String> siteMapBuilder = new BiConsumer<File, String>() {
			
			@Override
			public void accept(File file, String path) {
				if (path.endsWith(".html")) {
					try {
						WebSitemapUrl url = new WebSitemapUrl.Options(domain + "/" + path)
							    .lastMod(new Date(file.lastModified())).changeFreq(ChangeFreq.WEEKLY).build();
						wsg.addUrl(url); 
					} catch (MalformedURLException e) {
						throw new NasdanikaException(e);
					}
				}
			}
		};
		
		org.nasdanika.common.Util.walk(null, siteMapBuilder, outputDir.listFiles());
		wsg.write();
		
		// Search and inspection
		AtomicInteger problems = new AtomicInteger();
		JSONObject searchDocuments = new JSONObject();
		File siteDir = new File(outputDir, name);
		
		BiConsumer<File, String> searchBuilder = new BiConsumer<File, String>() {
			
			@Override
			public void accept(File file, String path) {
				if (path.endsWith(".html") && !"search.html".equals(path)) {
					try {	
						Predicate<String> predicate = org.nasdanika.html.model.app.gen.Util.createRelativeLinkPredicate(file, siteDir);						
						java.util.function.Consumer<? super Element> inspector = org.nasdanika.html.model.app.gen.Util.createInspector(predicate, error -> {
							System.err.println("[" + path +"] " + error);
							problems.incrementAndGet();
						});
						JSONObject searchDocument = org.nasdanika.html.model.app.gen.Util.createSearchDocument(path, file, inspector, null);
						if (searchDocument != null) {
							searchDocuments.put(path, searchDocument);
						}
					} catch (IOException e) {
						throw new NasdanikaException(e);
					}
				}
			}
		};

		org.nasdanika.common.Util.walk(null, searchBuilder, siteDir.listFiles());
		
		try (FileWriter writer = new FileWriter(new File(siteDir, "search-documents.js"))) {
			writer.write("var searchDocuments = " + searchDocuments);
		}
		
		if (problems.get() > 0) {
			fail("There are broken links: " + problems.get());
		};		
	}
	
	protected ResourceSet createResourceSet() {
		// Load model from XMI
		ResourceSet resourceSet = new NcoreResourceSet();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
	
		resourceSet.getPackageRegistry().put(NcorePackage.eNS_URI, NcorePackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(DiagramPackage.eNS_URI, DiagramPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ExecPackage.eNS_URI, ExecPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ContentPackage.eNS_URI, ContentPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ResourcesPackage.eNS_URI, ResourcesPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(HtmlPackage.eNS_URI, HtmlPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(BootstrapPackage.eNS_URI, BootstrapPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(AppPackage.eNS_URI, AppPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(FlowPackage.eNS_URI, FlowPackage.eINSTANCE);
		return resourceSet;
	}
	
	@Test
	public void generate() throws Exception {
		generateSite("adm");

		long cacheMisses = FeatureCacheAdapter.getMisses();
		long cacheCalls = FeatureCacheAdapter.getCalls();
		long cacheEfficiency = cacheCalls == 0 ? 0 : 100*(cacheCalls - cacheMisses)/cacheCalls;
		System.out.println("Feature cache - calls: " + cacheCalls + ", misses: " + cacheMisses + ", efficiency: " + cacheEfficiency + "%, compute time: " + FeatureCacheAdapter.getComputeTime() + " nanoseconds.");
	}
	
	private void generateSite(String name) throws Exception {
		System.out.println("Generating site: " + name);
		ProgressMonitor progressMonitor = new NullProgressMonitor(); // PrintStreamProgressMonitor();
		
		long start = System.currentTimeMillis();
		generateInstanceModel(name, Context.EMPTY_CONTEXT, progressMonitor);
		System.out.println("\tGenerated instance model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		generateActionModel(name, progressMonitor);
		System.out.println("\tGenerated action model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		generateResourceModel(name, progressMonitor);
		System.out.println("\tGenerated resource model in " + (System.currentTimeMillis() - start) + " milliseconds");
		start = System.currentTimeMillis();
		
		generateContainer(name, progressMonitor);
		System.out.println("\tGenerated site in " + (System.currentTimeMillis() - start) + " milliseconds");
	}
	
}
